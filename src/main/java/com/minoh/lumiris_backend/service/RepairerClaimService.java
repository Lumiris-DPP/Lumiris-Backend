package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.RepairerClaimPreview;
import com.minoh.lumiris_backend.dto.out.RepairerProfileResponse;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Réclamation d'une fiche annuaire : un retoucheur listé sans compte suit le lien de son e-mail
 * de prospection, s'inscrit, et rattache la fiche <b>existante</b> à son compte — jamais un
 * doublon. La fiche repasse en {@code PENDING} : un compte réel doit passer la revue KYB avant
 * d'agir comme retoucheur vérifié.
 */
@Service
@RequiredArgsConstructor
public class RepairerClaimService {

    private final RepairerProfileRepository repairerRepo;
    private final UserRepository userRepo;
    private final RepairerOnboardingService onboardingService;

    @Transactional(readOnly = true)
    public RepairerClaimPreview resolveToken(UUID token) {
        RepairerProfile profile = claimableByToken(token);
        return new RepairerClaimPreview(
                profile.getDisplayName(),
                profile.getCompanyName(),
                profile.getSiret(),
                profile.getAddress(),
                profile.getCity(),
                profile.getRegion(),
                profile.getSpecialties()
        );
    }

    @Transactional
    public RepairerProfileResponse claim(String userEmail, UUID token) {
        User user = userRepo.getByEmail(userEmail);
        if (repairerRepo.findByUser(user).isPresent()) {
            throw new ConflictException("Ce compte a déjà un profil retoucheur.");
        }
        RepairerProfile profile = claimableByToken(token);
        profile.setUser(user);
        profile.setStatus(RepairerStatus.PENDING);
        profile.setClaimedAt(Instant.now());
        profile.setClaimToken(null);
        return onboardingService.toResponse(repairerRepo.save(profile));
    }

    // Admin / prospection : (re)génère un jeton pour une fiche encore sans compte.
    @Transactional
    public UUID issueClaimToken(UUID profileId) {
        RepairerProfile profile = repairerRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche retoucheur introuvable : " + profileId));
        if (profile.getUser() != null) {
            throw new ConflictException("Cette fiche est déjà rattachée à un compte.");
        }
        UUID token = UUID.randomUUID();
        profile.setClaimToken(token);
        repairerRepo.save(profile);
        return token;
    }

    private RepairerProfile claimableByToken(UUID token) {
        return repairerRepo.findByClaimToken(token)
                .filter(p -> p.getUser() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Lien de réclamation invalide ou déjà utilisé."));
    }
}
