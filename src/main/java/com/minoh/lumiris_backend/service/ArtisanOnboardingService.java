package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.ArtisanRegisterRequest;
import com.minoh.lumiris_backend.dto.in.ArtisanStatusUpdateRequest;
import com.minoh.lumiris_backend.dto.out.ArtisanProfileResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtisanOnboardingService {

    private final ArtisanProfileRepository artisanRepo;
    private final UserRepository userRepo;
    private final SireneService sireneService;
    private final CmaService cmaService;
    private final MailService mailService;

    @Transactional
    public ArtisanProfileResponse register(String userEmail, ArtisanRegisterRequest request) {
        User user = findUser(userEmail);

        SireneService.SireneData sirene = sireneService.validate(request.siret());
        if (!cmaService.isRegisteredArtisan(request.siret())) {
            throw new IllegalArgumentException("SIRET non enregistré à la CMA");
        }

        ArtisanProfile profile = artisanRepo.findByUser(user).orElseGet(() -> {
            ArtisanProfile p = new ArtisanProfile();
            p.setUser(user);
            p.setJoinedAt(Instant.now());
            return p;
        });

        profile.setSiret(request.siret());
        profile.setStatus(ArtisanStatus.PENDING);
        profile.setCompanyName(sirene.companyName());
        profile.setNafCode(sirene.nafCode());
        profile.setSireneRawData(sirene.rawJson());

        return toResponse(artisanRepo.save(profile));
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

    // Admin actions

    public List<ArtisanProfileResponse> findPending() {
        return artisanRepo.findByStatus(ArtisanStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ArtisanProfileResponse verify(UUID profileId) {
        ArtisanProfile profile = findProfile(profileId);
        profile.setStatus(ArtisanStatus.VERIFIED);
        profile.setRejectionReason(null);
        ArtisanProfileResponse response = toResponse(artisanRepo.save(profile));
        mailService.sendVerified(profile.getUser().getEmail(), profile.getUser().getName());
        return response;
    }

    @Transactional
    public ArtisanProfileResponse reject(UUID profileId, ArtisanStatusUpdateRequest request) {
        ArtisanProfile profile = findProfile(profileId);
        profile.setStatus(ArtisanStatus.REJECTED);
        profile.setRejectionReason(request.reason());
        ArtisanProfileResponse response = toResponse(artisanRepo.save(profile));
        mailService.sendRejected(profile.getUser().getEmail(), profile.getUser().getName(), request.reason());
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

    private ArtisanProfileResponse toResponse(ArtisanProfile p) {
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
                p.getCreatedAt()
        );
    }
}
