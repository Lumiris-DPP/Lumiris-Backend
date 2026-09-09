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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * Suivi de tunnel : chaque envoi porte un jeton et passe par des liens tracés
 * ({@code /v1/prospecting/open|click|unsubscribe/{id}}) ; {@link RepairerProspectingReminderScheduler}
 * relance les prospects restés silencieux.
 */
@Service
@RequiredArgsConstructor
public class RepairerProspectingService {

    private static final Logger log = LoggerFactory.getLogger(RepairerProspectingService.class);

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
        String email = normalize(rawEmail);
        if (email.isEmpty()) {
            throw new ConflictException("Adresse e-mail manquante.");
        }
        if (suppressionRepo.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Cette adresse s'est désinscrite de la prospection.");
        }

        RepairerProfile profile = repairerRepo.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche retoucheur introuvable : " + profileId));

        UUID token = claimService.issueClaimToken(profileId, email); // lie le jeton à l'adresse invitée

        RepairerProspectOutreach outreach = new RepairerProspectOutreach();
        outreach.setRepairerProfile(profile);
        outreach.setEmail(email);
        outreach.setToken(token);
        outreach.setSentAt(Instant.now());
        outreach.setLastContactedAt(Instant.now());
        outreach = outreachRepo.save(outreach);

        send(outreach, profile);
    }

    // Relance : réutilise le jeton (il n'expire pas), incrémente le compteur.
    @Transactional
    public void followUp(UUID outreachId) {
        RepairerProspectOutreach outreach = outreachRepo.findById(outreachId)
                .orElseThrow(() -> new ResourceNotFoundException("Envoi introuvable : " + outreachId));
        if (outreach.getClaimedAt() != null || outreach.getUnsubscribedAt() != null) {
            return;
        }
        if (suppressionRepo.existsByEmailIgnoreCase(outreach.getEmail())) {
            return;
        }
        outreach.setContactCount(outreach.getContactCount() + 1);
        outreach.setLastContactedAt(Instant.now());
        send(outreach, outreach.getRepairerProfile());
    }

    private void send(RepairerProspectOutreach outreach, RepairerProfile profile) {
        String base = publicBaseUrl + "/v1/prospecting";
        String claimUrl = base + "/click/" + outreach.getId();
        String openPixelUrl = base + "/open/" + outreach.getId();
        String unsubscribeUrl = base + "/unsubscribe/" + outreach.getId();
        mailService.sendRepairerProspecting(
                outreach.getEmail(), profile.getDisplayName(), claimUrl, openPixelUrl, unsubscribeUrl);
    }

    // Cible réelle du lien de réclamation, servie après enregistrement du clic.
    public String claimLandingUrl(UUID token) {
        return UriComponentsBuilder.fromUriString(repairerAppUrl)
                .path("/retoucheurs/reclamer").queryParam("token", token).toUriString();
    }

    @Transactional
    public String recordClick(UUID outreachId) {
        RepairerProspectOutreach outreach = outreachRepo.findById(outreachId)
                .orElseThrow(() -> new ResourceNotFoundException("Lien inconnu."));
        if (outreach.getClickedAt() == null) {
            outreach.setClickedAt(Instant.now());
        }
        return claimLandingUrl(outreach.getToken());
    }

    @Transactional
    public void recordOpen(UUID outreachId) {
        outreachRepo.findById(outreachId).ifPresent(o -> {
            if (o.getOpenedAt() == null) {
                o.setOpenedAt(Instant.now());
            }
        });
    }

    @Transactional
    public void unsubscribe(UUID outreachId) {
        RepairerProspectOutreach outreach = outreachRepo.findById(outreachId)
                .orElseThrow(() -> new ResourceNotFoundException("Lien de désinscription inconnu."));
        outreach.setUnsubscribedAt(Instant.now());
        suppress(outreach.getEmail(), EmailSuppression.Reason.UNSUBSCRIBE);
    }

    // Depuis le webhook Resend (bounce dur / plainte) : on ne recontacte plus cette adresse.
    @Transactional
    public void suppress(String rawEmail, EmailSuppression.Reason reason) {
        String email = normalize(rawEmail);
        if (email.isEmpty() || suppressionRepo.existsByEmailIgnoreCase(email)) {
            return;
        }
        suppressionRepo.save(new EmailSuppression(email, reason));
        log.info("Prospection : {} ajoutée à la liste de suppression ({})", email, reason);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
