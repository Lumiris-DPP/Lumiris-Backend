package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * RGPD — purge des fiches annuaire jamais réclamées : importées il y a plus de N mois (18 par
 * défaut) et pas recontactées depuis 6 mois. On ne conserve pas indéfiniment les coordonnées de
 * professionnels qui n'ont jamais engagé avec Lumiris.
 *
 * Hebdomadaire : c'est de l'entretien, pas un événement.
 */
@Component
@RequiredArgsConstructor
public class RepairerDirectoryPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(RepairerDirectoryPurgeScheduler.class);
    private static final long WEEKLY_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final int RECENT_CONTACT_MONTHS = 6;

    private final RepairerProfileRepository repairerRepo;

    @Value("${lumiris.repairer.unclaimed-retention-months:18}")
    private int retentionMonths;

    @Scheduled(fixedDelay = WEEKLY_MS, initialDelay = WEEKLY_MS / 7)
    @Transactional
    public void purgeStaleUnclaimedListings() {
        Instant now = Instant.now();
        List<RepairerProfile> stale = repairerRepo.findPurgeableUnclaimed(
                now.minus(retentionMonths * 30L, ChronoUnit.DAYS),
                now.minus(RECENT_CONTACT_MONTHS * 30L, ChronoUnit.DAYS));
        if (!stale.isEmpty()) {
            repairerRepo.deleteAll(stale);
            log.info("RGPD : {} fiche(s) annuaire jamais réclamée(s) purgée(s)", stale.size());
        }
    }
}
