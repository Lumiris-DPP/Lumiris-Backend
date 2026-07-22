package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.ArtisanVitrineUpdateRequest;
import com.minoh.lumiris_backend.dto.out.ArtisanPhotoResponse;
import com.minoh.lumiris_backend.dto.out.ArtisanProfileResponse;
import com.minoh.lumiris_backend.dto.out.ArtisanPublicProfileResponse;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanProfilePhoto;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ArtisanNotVerifiedException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.ArtisanProfilePhotoRepository;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArtisanVitrineService {

    private final ArtisanProfileRepository artisanRepo;
    private final ArtisanProfilePhotoRepository photoRepo;
    private final StoredFileRepository storedFileRepo;
    private final UserRepository userRepo;
    private final StorageService storageService;
    private final ArtisanOnboardingService onboardingService;

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");

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

    @Transactional(readOnly = true)
    public ArtisanPublicProfileResponse findPublicBySlug(String slug) {
        ArtisanProfile profile = artisanRepo.findBySlug(slug)
                .filter(ArtisanProfile::isPublished)
                .filter(p -> p.getStatus() == ArtisanStatus.VERIFIED)
                .orElseThrow(() -> new ResourceNotFoundException("Artisan introuvable"));

        return toPublicResponse(profile);
    }

    // Public directory listing (no geo-search: artisans have no stored coordinates, unlike
    // repairers) — every published, verified atelier, for VISION's /local hub.
    @Transactional(readOnly = true)
    public List<ArtisanPublicProfileResponse> findAllPublished() {
        return artisanRepo.findByPublishedTrueAndStatus(ArtisanStatus.VERIFIED).stream()
                .map(this::toPublicResponse)
                .toList();
    }

    private ArtisanPublicProfileResponse toPublicResponse(ArtisanProfile profile) {
        List<String> photoUrls = photoRepo.findByArtisanProfileOrderByPosition(profile).stream()
                .map(photo -> storageService.getPresignedUrl(photo.getFile().getId()))
                .toList();

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
                profile.isOekoTexLabeled()
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
