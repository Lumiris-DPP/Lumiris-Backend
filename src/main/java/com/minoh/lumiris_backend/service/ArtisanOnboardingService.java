package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.ArtisanRegisterRequest;
import com.minoh.lumiris_backend.dto.in.RejectionRequest;
import com.minoh.lumiris_backend.dto.in.KybDetailsRequest;
import com.minoh.lumiris_backend.dto.out.ArtisanPhotoResponse;
import com.minoh.lumiris_backend.dto.out.ArtisanProfileResponse;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.entity.KybDocumentLabel;
import com.minoh.lumiris_backend.entity.KybStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.ArtisanProfilePhotoRepository;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtisanOnboardingService {

    private final ArtisanProfileRepository artisanRepo;
    private final ArtisanProfilePhotoRepository photoRepo;
    private final UserRepository userRepo;
    private final SireneService sireneService;
    private final MailService mailService;
    private final StorageService storageService;
    private final KybMapper kybMapper;
    private final OcrService ocrService;

    @Transactional(readOnly = true)
    public ArtisanProfileResponse findByUserEmail(String userEmail) {
        User user = findUser(userEmail);
        return artisanRepo.findByUser(user)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Profil artisan introuvable"));
    }

    @Transactional
    public ArtisanProfileResponse register(String userEmail, ArtisanRegisterRequest request) {
        User user = findUser(userEmail);
        ArtisanProfile profile = verifySiret(user, request.siret());
        return toResponse(artisanRepo.save(profile));
    }

    @Transactional
    public void verifySiretOnSignup(User user, String siret) {
        ArtisanProfile profile = verifySiret(user, siret);
        artisanRepo.save(profile);
    }

    private ArtisanProfile verifySiret(User user, String siret) {
        SireneService.SireneData sirene = sireneService.validate(siret);

        ArtisanProfile profile = artisanRepo.findByUser(user).orElseGet(() -> {
            ArtisanProfile p = new ArtisanProfile();
            p.setUser(user);
            p.setJoinedAt(Instant.now());
            return p;
        });

        profile.setSiret(siret);
        profile.setStatus(ArtisanStatus.PENDING);
        profile.setCompanyName(sirene.companyName());
        profile.setNafCode(sirene.nafCode());
        profile.setSireneRawData(sirene.rawJson());
        if (profile.getAtelierName() == null || profile.getAtelierName().isBlank()) {
            profile.setAtelierName(sirene.companyName());
        }
        profile.getKyb().setSireneSiren(sirene.siren());
        profile.getKyb().setSireneSiegeAddress(sirene.siegeAddress());
        profile.getKyb().setSireneNatureJuridique(sirene.natureJuridique());
        profile.getKyb().setSireneDirigeantsJson(sirene.dirigeantsJson());
        return profile;
    }

    @Transactional
    public ArtisanProfileResponse signDeclaration(String userEmail, String clientIp) {
        User user = findUser(userEmail);
        ArtisanProfile profile = artisanRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil artisan introuvable"));

        profile.setDeclarationSigned(true);
        profile.setSignatureTimestamp(Instant.now());
        profile.setSignatureIp(clientIp);

        ArtisanProfileResponse response = toResponse(artisanRepo.save(profile));
        mailService.sendRegistrationPending(user.getEmail(), user.getName());
        return response;
    }

    @Transactional
    public ArtisanProfileResponse submitKyb(String userEmail, KybDetailsRequest request) {
        User user = findUser(userEmail);
        ArtisanProfile profile = artisanRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil artisan introuvable"));

        kybMapper.applyRequest(profile.getKyb(), request);

        return toResponse(artisanRepo.save(profile));
    }

    @Transactional
    public ArtisanProfileResponse uploadKybDocument(
            String userEmail, KybDocumentLabel label, MultipartFile file, LocalDate expiresAt
    ) {
        User user = findUser(userEmail);
        ArtisanProfile profile = artisanRepo.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Profil artisan introuvable"));

        FileUploadResponse uploaded = storageService.upload(file, userEmail);
        switch (label) {
            case legal_representative_id_doc -> {
                profile.getKyb().setIdDocFileId(uploaded.id());
                profile.getKyb().setIdDocExpiresAt(expiresAt);
                ocrService.extractText(file).ifPresent(profile.getKyb()::setIdDocOcrText);
            }
            case kbis -> {
                profile.getKyb().setKbisFileId(uploaded.id());
                profile.getKyb().setKbisExpiresAt(expiresAt);
            }
            case proof_of_address -> {
                profile.getKyb().setProofOfAddressFileId(uploaded.id());
                profile.getKyb().setProofOfAddressExpiresAt(expiresAt);
            }
            case rib -> {
                profile.getKyb().setRibFileId(uploaded.id());
                profile.getKyb().setRibExpiresAt(expiresAt);
            }
        }

        return toResponse(artisanRepo.save(profile));
    }

    // Admin actions

    public List<ArtisanProfileResponse> findPending() {
        return artisanRepo.findByStatus(ArtisanStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    // Every registered artisan account (any status), for the admin's general account browser —
    // as opposed to findPending() which only surfaces dossiers awaiting review.
    public List<ArtisanProfileResponse> findAll() {
        return artisanRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ArtisanProfileResponse verify(UUID profileId) {
        return updateKybStatus(profileId, KybStatus.VALIDATED, null);
    }

    @Transactional
    public ArtisanProfileResponse reject(UUID profileId, RejectionRequest request) {
        return updateKybStatus(profileId, KybStatus.REJECTED, request.reason());
    }

    // Marks a dossier as under active review — no account-status or email side effect.
    @Transactional
    public ArtisanProfileResponse markKybOngoing(UUID profileId) {
        ArtisanProfile profile = findProfile(profileId);
        profile.getKyb().setKybStatus(KybStatus.ONGOING);
        return toResponse(artisanRepo.save(profile));
    }

    // Sends the dossier back to the artisan with a note on what's missing/wrong, without a hard
    // rejection — the account stays PENDING so they can fix and resubmit.
    @Transactional
    public ArtisanProfileResponse markKybIncomplete(UUID profileId, String note) {
        ArtisanProfile profile = findProfile(profileId);
        profile.getKyb().setKybStatus(KybStatus.INCOMPLETE);
        profile.getKyb().setKybReviewNote(note);
        ArtisanProfileResponse response = toResponse(artisanRepo.save(profile));
        mailService.sendKybIncomplete(profile.getUser().getEmail(), profile.getUser().getName(), note);
        return response;
    }

    private ArtisanProfileResponse updateKybStatus(UUID profileId, KybStatus status, String note) {
        ArtisanProfile profile = findProfile(profileId);
        profile.getKyb().setKybStatus(status);
        profile.getKyb().setKybReviewNote(note);
        if (status == KybStatus.VALIDATED) {
            profile.setStatus(ArtisanStatus.VERIFIED);
            profile.setRejectionReason(null);
        } else if (status == KybStatus.REJECTED) {
            profile.setStatus(ArtisanStatus.REJECTED);
            profile.setRejectionReason(note);
        }
        ArtisanProfileResponse response = toResponse(artisanRepo.save(profile));
        if (status == KybStatus.VALIDATED) {
            mailService.sendVerified(profile.getUser().getEmail(), profile.getUser().getName());
        } else if (status == KybStatus.REJECTED) {
            mailService.sendRejected(profile.getUser().getEmail(), profile.getUser().getName(), note);
        }
        return response;
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private ArtisanProfile findProfile(UUID id) {
        return artisanRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil artisan introuvable : " + id));
    }

    // La pause n'est exposée que si elle est encore à venir : le front n'a ainsi aucune horloge
    // à comparer pour savoir si l'atelier est réellement en congés.
    private static Instant activePause(ArtisanProfile p) {
        Instant until = p.getPausedUntil();
        return until != null && until.isAfter(Instant.now()) ? until : null;
    }

    ArtisanProfileResponse toResponse(ArtisanProfile p) {
        return new ArtisanProfileResponse(
                p.getId(),
                p.getUser().getEmail(),
                p.getUser().getName(),
                p.getStatus(),
                p.getSiret(),
                p.getCompanyName(),
                p.getNafCode(),
                p.isDeclarationSigned(),
                p.getSignatureTimestamp(),
                p.getRejectionReason(),
                p.getCreatedAt(),
                p.getSlug(),
                p.isPublished(),
                activePause(p),
                p.getAtelierName(),
                p.getStory(),
                p.getMethod(),
                p.getJourney(),
                p.getSpecialties(),
                p.getCity(),
                p.getRegion(),
                p.getWebsiteUrl(),
                p.getLinks(),
                photoRepo.findByArtisanProfileOrderByPosition(p).stream()
                        .map(photo -> new ArtisanPhotoResponse(
                                photo.getId(),
                                storageService.getPresignedUrl(photo.getFile().getId())
                        ))
                        .toList(),
                kybMapper.toResponse(p.getKyb())
        );
    }
}
