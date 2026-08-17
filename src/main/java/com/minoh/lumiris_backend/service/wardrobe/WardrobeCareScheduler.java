package com.minoh.lumiris_backend.service.wardrobe;

import com.minoh.lumiris_backend.entity.WardrobeItem;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

// Garde-Robe active. Une fois la pièce reçue, l'app n'avait plus rien à dire — alors que c'est la
// période la plus longue de la relation. Ce balayage produit les deux seules relances qu'on puisse
// envoyer sans rien vendre : l'entretien, tiré des symboles du passeport, et la garantie qui
// s'achève. Les deux rouvrent Lumiris pour une raison légitime.
//
// Quotidien et non horaire : ce sont des échéances de saison et de mois, pas des événements.
@Component
@RequiredArgsConstructor
public class WardrobeCareScheduler {

    private static final Logger log = LoggerFactory.getLogger(WardrobeCareScheduler.class);
    private static final long DAILY_MS = 24 * 60 * 60 * 1000L;

    // Laisse passer la fenêtre de rétractation : conseiller l'entretien d'une pièce reçue il y a
    // trois jours, alors que l'acheteur hésite encore à la garder, sonne faux.
    private static final Duration SETTLED_AFTER = Duration.ofDays(30);

    // Assez tôt pour qu'un défaut puisse encore être signalé et traité par l'atelier, assez tard
    // pour que l'échéance soit concrète.
    private static final Duration WARRANTY_NOTICE = Duration.ofDays(30);

    // Soupape : une Garde-Robe pathologique ne doit pas saturer l'envoi d'e-mails en un passage.
    private static final int MAX_ALERTS_PER_RUN = 500;

    private final WardrobeItemRepository wardrobeItemRepository;
    private final WardrobeCareRecorder recorder;

    @Scheduled(fixedDelay = DAILY_MS, initialDelay = DAILY_MS / 4)
    public void sweep() {
        Instant now = Instant.now();
        int care = sweepCareReminders(now);
        int warranty = sweepWarranties(now);
        if (care > 0 || warranty > 0) {
            log.info("Garde-Robe active : {} rappel(s) d'entretien, {} garantie(s) bientôt échue(s)",
                    care, warranty);
        }
    }

    // Hors saison, rien du tout : un rappel d'entretien en plein janvier ou en plein juillet n'a
    // aucun sens, et le bruit tue la crédibilité des rares alertes qui comptent.
    private int sweepCareReminders(Instant now) {
        CareSeason season = CareSeason.at(now);
        if (season == null) {
            return 0;
        }
        String seasonKey = season.keyFor(now);
        List<WardrobeItem> candidates = wardrobeItemRepository.findCareReminderCandidates(
                seasonKey, now.minus(SETTLED_AFTER));
        return sendAll(candidates, item -> recorder.sendCareReminder(item, season, seasonKey));
    }

    private int sweepWarranties(Instant now) {
        return sendAll(wardrobeItemRepository.findExpiringWarranties(now, now.plus(WARRANTY_NOTICE)),
                recorder::sendWarrantyAlert);
    }

    private int sendAll(List<WardrobeItem> candidates, Predicate<WardrobeItem> send) {
        int sent = 0;
        for (WardrobeItem item : candidates) {
            if (sent >= MAX_ALERTS_PER_RUN) {
                log.warn("Plafond de rappels Garde-Robe atteint ({}) : le reste part au prochain passage",
                        MAX_ALERTS_PER_RUN);
                break;
            }
            if (send.test(item)) {
                sent++;
            }
        }
        return sent;
    }
}
