package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.RepairerClaimPreview;
import com.minoh.lumiris_backend.dto.out.RepairerProfileResponse;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.RepairerProspectOutreachRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Réclamation d'une fiche annuaire : un retoucheur listé sans compte suit le lien de son e-mail
 * de prospection, s'inscrit, et rattache la fiche <b>existante</b> à son compte — jamais un
 * doublon. La fiche repasse en {@code PENDING} : un compte réel doit passer la revue KYB avant
 * d'agir comme retoucheur vérifié.
 *
 * Le jeton expire (30 j) et, quand il a été envoyé par e-mail, la réclamation exige que le
 * compte utilise cette même adresse — un lien transféré ne sert pas à un tiers.
 */
@Service
@RequiredArgsConstructor
public class RepairerClaimService {

    private final RepairerProfileRepository repairerRepo;
    private final RepairerProspectOutreachRepository outreachRepo;
    private final UserRepository userRepo;
    private final RepairerOnboardingService onboardingService;

    @Value("${lumiris.repairer.claim-token-ttl-days:30}")
    private int tokenTtlDays;

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

        String boundEmail = profile.getClaimTokenEmail();
        if (boundEmail != null && !boundEmail.equalsIgnoreCase(userEmail)) {
            throw new ConflictException(
                    "Ce lien de réclamation a été envoyé à une autre adresse. Créez votre compte avec cette adresse.");
        }

        profile.setUser(user);
        profile.setStatus(RepairerStatus.PENDING);
        profile.setClaimedAt(Instant.now());
        profile.setClaimToken(null);
        profile.setClaimTokenEmail(null);
        profile.setClaimTokenExpiresAt(null);

        // Relie la conversion à l'e-mail de prospection, s'il y en a un.
        outreachRepo.findByToken(token).ifPresent(o -> {
            if (o.getClaimedAt() == null) {
                o.setClaimedAt(Instant.now());
            }
        });

        return onboardingService.toResponse(repairerRepo.save(profile));
    }

    // Admin (lien non nominatif) : (re)génère un jeton pour une fiche encore sans compte.
    @Transactional
    public UUID issueClaimToken(UUID profileId) {
        return issueClaimToken(profileId, null);
    }

    // Prospection : jeton lié à l'adresse invitée.
    @Transactional
    public UUID issueClaimToken(UUID profileId, String email) {
        RepairerProfile profile = repairerRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche retoucheur introuvable : " + profileId));
        if (profile.getUser() != null) {
            throw new ConflictException("Cette fiche est déjà rattachée à un compte.");
        }
        UUID token = UUID.randomUUID();
        profile.setClaimToken(token);
        profile.setClaimTokenEmail(email == null ? null : email.trim().toLowerCase());
        profile.setClaimTokenExpiresAt(Instant.now().plus(tokenTtlDays, ChronoUnit.DAYS));
        repairerRepo.save(profile);
        return token;
    }

    private RepairerProfile claimableByToken(UUID token) {
        RepairerProfile profile = repairerRepo.findByClaimToken(token)
                .filter(p -> p.getUser() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Lien de réclamation invalide ou déjà utilisé."));
        if (profile.getClaimTokenExpiresAt() != null && profile.getClaimTokenExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("Ce lien de réclamation a expiré. Demandez-en un nouveau.");
        }
        return profile;
    }
}
