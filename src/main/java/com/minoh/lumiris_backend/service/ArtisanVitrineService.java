package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.ArtisanVitrineUpdateRequest;
import com.minoh.lumiris_backend.dto.out.ArtisanPhotoResponse;
import com.minoh.lumiris_backend.dto.out.ArtisanProfileResponse;
import com.minoh.lumiris_backend.dto.out.ArtisanPublicPieceResponse;
import com.minoh.lumiris_backend.dto.out.ArtisanPublicProfileResponse;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanProfilePhoto;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ArtisanNotVerifiedException;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.ArtisanProfilePhotoRepository;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArtisanVitrineService {

    private final ArtisanProfileRepository artisanRepo;
    private final DppFormRepository dppFormRepo;
    private final IrisScoreRepository irisScoreRepo;
    private final ArtisanProfilePhotoRepository photoRepo;
    private final StoredFileRepository storedFileRepo;
    private final UserRepository userRepo;
    private final StorageService storageService;
    private final ArtisanOnboardingService onboardingService;
    private final PreparationDelayResolver preparationDelayResolver;
    private final ArtisanPhotoUrlResolver photoUrlResolver;

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");

    // Au-delà, ce ne sont plus des congés mais une fermeture : le délai annoncé à l'acheteur
    // deviendrait une promesse que personne ne peut tenir.
    private static final Duration MAX_PAUSE = Duration.ofDays(180);

    @Transactional
    public ArtisanProfileResponse updateProfile(String userEmail, ArtisanVitrineUpdateRequest request) {
        ArtisanProfile profile = findProfile(userEmail);

        profile.setAtelierName(request.atelierName());
        profile.setStory(request.story());
        profile.setMethod(request.method());
        profile.setJourney(request.journey());
        profile.setSpecialties(request.specialties());
        profile.setCity(request.city());
        profile.setRegion(request.region());
        profile.setWebsiteUrl(request.websiteUrl());
        profile.setLinks(request.links());

        return onboardingService.toResponse(artisanRepo.save(profile));
    }

    @Transactional
    public ArtisanPhotoResponse addPhoto(String userEmail, MultipartFile file) {
        ArtisanProfile profile = findProfile(userEmail);

        FileUploadResponse uploaded = storageService.upload(file, userEmail);
        int nextPosition = photoRepo.countByArtisanProfile(profile);

        ArtisanProfilePhoto photo = new ArtisanProfilePhoto();
        photo.setArtisanProfile(profile);
        photo.setFile(storedFileRepo.getReferenceById(uploaded.id()));
        photo.setPosition(nextPosition);
        photo = photoRepo.save(photo);

        return new ArtisanPhotoResponse(photo.getId(), storageService.getPresignedUrl(uploaded.id()));
    }

    @Transactional
    public void removePhoto(String userEmail, UUID photoId) {
        ArtisanProfile profile = findProfile(userEmail);
        ArtisanProfilePhoto photo = photoRepo.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo introuvable"));

        if (!photo.getArtisanProfile().getId().equals(profile.getId())) {
            throw new ResourceNotFoundException("Photo introuvable");
        }
        photoRepo.delete(photo);
    }

    @Transactional
    public ArtisanProfileResponse publish(String userEmail) {
        ArtisanProfile profile = findProfile(userEmail);

        if (profile.getStatus() != ArtisanStatus.VERIFIED) {
            throw new ArtisanNotVerifiedException("Le profil doit être vérifié (KYB) avant publication.");
        }
        if (profile.getSlug() == null || profile.getSlug().isBlank()) {
            profile.setSlug(generateUniqueSlug(profile));
        }
        profile.setPublished(true);

        return onboardingService.toResponse(artisanRepo.save(profile));
    }

    // Congés : les pièces restent achetables, le délai d'expédition annoncé est simplement allongé
    // jusqu'à la date de retour. Aucun statut produit n'est touché, donc rien à restaurer au retour.
    @Transactional
    public ArtisanProfileResponse pause(String userEmail, Instant until) {
        ArtisanProfile profile = findProfile(userEmail);
        if (until.isAfter(Instant.now().plus(MAX_PAUSE))) {
            throw new BillingValidationException(
                    "Une pause ne peut pas dépasser 180 jours. Archivez vos annonces pour une fermeture plus longue.");
        }
        profile.setPausedUntil(until);
        return onboardingService.toResponse(artisanRepo.save(profile));
    }

    @Transactional
    public ArtisanProfileResponse resume(String userEmail) {
        ArtisanProfile profile = findProfile(userEmail);
        profile.setPausedUntil(null);
        return onboardingService.toResponse(artisanRepo.save(profile));
    }

    private static final List<ArtisanStatus> PUBLIC_STATUSES = List.of(ArtisanStatus.VERIFIED, ArtisanStatus.UNCLAIMED);

    @Transactional(readOnly = true)
    public ArtisanPublicProfileResponse findPublicBySlug(String slug) {
        ArtisanProfile profile = requirePublishedProfile(slug);
        return toPublicProfile(profile, photoUrlResolver.of(profile));
    }

    @Transactional(readOnly = true)
    public List<ArtisanPublicPieceResponse> listPublicPieces(String slug) {
        ArtisanProfile profile = requirePublishedProfile(slug);
        if (profile.getUser() == null) {
            return List.of();
        }

        List<DppForm> publishedForms = dppFormRepo.findByUserId(profile.getUser().getId()).stream()
                .filter(form -> form.getPublicCode() != null)
                .toList();

        Map<UUID, IrisScore> scoresByFormId = irisScoreRepo
                .findByDppFormIdIn(publishedForms.stream().map(DppForm::getId).toList()).stream()
                .collect(Collectors.toMap(score -> score.getDppForm().getId(), score -> score));

        Map<UUID, String> urlsByFileId = storageService.getPresignedUrls(publishedForms.stream()
                .map(DppForm::getMainPhotoFile)
                .filter(Objects::nonNull)
                .map(StoredFile::getId)
                .toList());

        return publishedForms.stream()
                .map(form -> toPublicPiece(form, scoresByFormId.get(form.getId()), urlsByFileId))
                .toList();
    }

    private ArtisanProfile requirePublishedProfile(String slug) {
        return artisanRepo.findBySlug(slug)
                .filter(ArtisanProfile::isPublished)
                .filter(p -> PUBLIC_STATUSES.contains(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Artisan introuvable"));
    }

    // Public directory listing (no geo-search: artisans have no stored coordinates, unlike
    // repairers) — every published, verified atelier, for VISION's /local hub.
    @Transactional(readOnly = true)
    public List<ArtisanPublicProfileResponse> findAllPublished() {
        return toPublicProfiles(artisanRepo.findByPublishedTrueAndStatus(ArtisanStatus.VERIFIED));
    }

    // Bandeau "pas encore dans le réseau" (fiche annuaire sans compte) — signal d'intérêt anonyme,
    // sans email ni aucune autre donnée personnelle.
    @Transactional
    public void signalInterest(String slug) {
        ArtisanProfile profile = artisanRepo.findBySlug(slug)
                .filter(p -> p.getStatus() == ArtisanStatus.UNCLAIMED)
                .orElseThrow(() -> new ResourceNotFoundException("Artisan introuvable"));
        artisanRepo.incrementInterest(profile.getId());
    }

    private List<ArtisanPublicProfileResponse> toPublicProfiles(List<ArtisanProfile> profiles) {
        Map<UUID, List<ArtisanPhotoUrlResolver.PhotoUrl>> photos = photoUrlResolver.byProfileId(profiles);

        return profiles.stream()
                .map(profile -> toPublicProfile(profile, photos.getOrDefault(profile.getId(), List.of())))
                .toList();
    }

    private ArtisanPublicPieceResponse toPublicPiece(DppForm form, IrisScore score, Map<UUID, String> urlsByFileId) {
        String photoUrl = form.getMainPhotoFile() != null
                ? urlsByFileId.get(form.getMainPhotoFile().getId())
                : null;

        return new ArtisanPublicPieceResponse(
                form.getPublicCode(),
                form.getProductName(),
                form.getProductCategory(),
                photoUrl,
                score != null ? score.getTotal() : null,
                score != null ? score.getGrade() : null);
    }

    private ArtisanPublicProfileResponse toPublicProfile(ArtisanProfile profile,
                                                         List<ArtisanPhotoUrlResolver.PhotoUrl> photos) {
        List<String> photoUrls = photos.stream().map(ArtisanPhotoUrlResolver.PhotoUrl::url).toList();

        return new ArtisanPublicProfileResponse(
                profile.getSlug(),
                profile.getDisplayName(),
                profile.getAtelierName(),
                profile.getStory(),
                profile.getMethod(),
                profile.getJourney(),
                profile.getSpecialties(),
                profile.getCity(),
                profile.getRegion(),
                profile.getWebsiteUrl(),
                profile.getLinks(),
                photoUrls,
                profile.isEpvLabeled(),
                profile.isOfgLabeled(),
                profile.isGotsLabeled(),
                profile.isOekoTexLabeled(),
                preparationDelayResolver.activePauseUntil(profile, Instant.now()),
                profile.getUser() != null, profile.getInterestCount(),
                profile.getLocation() != null ? profile.getLocation().getY() : null,
                profile.getLocation() != null ? profile.getLocation().getX() : null
        );
    }

    private String generateUniqueSlug(ArtisanProfile profile) {
        String base = profile.getAtelierName() != null ? profile.getAtelierName() : profile.getDisplayName();
        String slugBase = slugify(base);

        String candidate = slugBase;
        int suffix = 2;
        while (artisanRepo.findBySlug(candidate).isPresent()) {
            candidate = slugBase + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        String slug = NON_SLUG_CHARS.matcher(normalized).replaceAll("-").replaceAll("^-|-$", "");
        return slug.isBlank() ? UUID.randomUUID().toString().substring(0, 8) : slug;
    }

    private ArtisanProfile findProfile(String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        return artisanRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil artisan introuvable"));
    }
}
