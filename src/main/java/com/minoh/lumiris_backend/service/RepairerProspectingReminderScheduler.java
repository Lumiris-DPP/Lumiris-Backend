package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.RepairerProspectOutreach;
import com.minoh.lumiris_backend.repository.RepairerProspectOutreachRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Relance des prospects retoucheurs restés silencieux : 2ᵉ contact à J+7, 3ᵉ à J+21, puis stop.
 * Un e-mail convertit peu ; la séquence multiplie le taux de réclamation. S'arrête dès qu'un
 * prospect réclame, se désinscrit ou passe en suppression.
 *
 * ponytail: pas de verrou distribué, aligné sur les autres schedulers (un seul réplica).
 * followUp est idempotent au sens où un double passage n'enverrait qu'un e-mail de plus, borné
 * par le plafond de 3 contacts.
 */
@Component
@RequiredArgsConstructor
public class RepairerProspectingReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(RepairerProspectingReminderScheduler.class);
    private static final long DAILY_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_CONTACTS = 3;
    private static final int FIRST_GAP_DAYS = 7;   // 1er → 2e contact
    private static final int SECOND_GAP_DAYS = 14;  // 2e → 3e contact (J+21 au total)
    private static final int MAX_PER_RUN = 200;

    private final RepairerProspectOutreachRepository outreachRepo;
    private final RepairerProspectingService prospectingService;

    @Scheduled(fixedDelay = DAILY_MS, initialDelay = DAILY_MS / 3)
    public void sendFollowUps() {
        Instant now = Instant.now();
        List<RepairerProspectOutreach> candidates =
                outreachRepo.findDueForFollowUp(MAX_CONTACTS, now.minus(FIRST_GAP_DAYS, ChronoUnit.DAYS));

        int sent = 0;
        for (RepairerProspectOutreach outreach : candidates) {
            if (sent >= MAX_PER_RUN) {
                break;
            }
            int gap = outreach.getContactCount() == 1 ? FIRST_GAP_DAYS : SECOND_GAP_DAYS;
            if (outreach.getLastContactedAt() == null
                    || !outreach.getLastContactedAt().isBefore(now.minus(gap, ChronoUnit.DAYS))) {
                continue;
            }
            prospectingService.followUp(outreach.getId());
            sent++;
        }
        if (sent > 0) {
            log.info("Prospection retoucheurs : {} relance(s) envoyée(s)", sent);
        }
    }
}
