package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.EmailSuppression;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerProspectOutreach;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.EmailSuppressionRepository;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.RepairerProspectOutreachRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Prospection : invite un retoucheur listé (fiche sans compte) à réclamer sa fiche via un e-mail.
 * Prospection B2B — destinataire professionnel, message lié à son métier, source des données
 * citée, désinscription en un clic (RGPD + LCEN art. L34-5). Toute adresse dans
 * {@code email_suppression} est écartée avant envoi.
 */
@Service
@RequiredArgsConstructor
public class RepairerProspectingService {

    private final RepairerProfileRepository repairerRepo;
    private final RepairerProspectOutreachRepository outreachRepo;
    private final EmailSuppressionRepository suppressionRepo;
    private final RepairerClaimService claimService;
    private final MailService mailService;

    // App ATELIER (client) — c'est là que les retoucheurs s'inscrivent, pas l'app consommateur.
    @Value("${app.repairer-app-url}")
    private String repairerAppUrl;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Transactional
    public void invite(UUID profileId, String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (email.isEmpty()) {
            throw new ConflictException("Adresse e-mail manquante.");
        }
        if (suppressionRepo.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Cette adresse s'est désinscrite de la prospection.");
        }

        RepairerProfile profile = repairerRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche retoucheur introuvable : " + profileId));

        UUID token = claimService.issueClaimToken(profileId); // vérifie que la fiche est sans compte

        RepairerProspectOutreach outreach = new RepairerProspectOutreach();
        outreach.setRepairerProfile(profile);
        outreach.setEmail(email);
        outreach.setSentAt(Instant.now());
        outreach = outreachRepo.save(outreach);

        String claimUrl = UriComponentsBuilder.fromUriString(repairerAppUrl)
                .path("/retoucheurs/reclamer").queryParam("token", token).toUriString();
        String unsubscribeUrl = UriComponentsBuilder.fromUriString(publicBaseUrl)
                .path("/v1/prospecting/unsubscribe/{id}").build(outreach.getId()).toString();

        mailService.sendRepairerProspecting(email, profile.getDisplayName(), claimUrl, unsubscribeUrl);
    }

    @Transactional
    public void unsubscribe(UUID outreachId) {
        RepairerProspectOutreach outreach = outreachRepo.findById(outreachId)
                .orElseThrow(() -> new ResourceNotFoundException("Lien de désinscription inconnu."));
        outreach.setUnsubscribedAt(Instant.now());

        if (!suppressionRepo.existsByEmailIgnoreCase(outreach.getEmail())) {
            suppressionRepo.save(new EmailSuppression(outreach.getEmail(), EmailSuppression.Reason.UNSUBSCRIBE));
        }
    }
}
