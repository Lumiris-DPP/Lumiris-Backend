package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import com.minoh.lumiris_backend.repository.MarketplaceFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Les deux seules relances qu'une marketplace puisse légitimement envoyer, parce qu'elles sont
// déclenchées par le stock et le prix RÉELS.
//
// En balayage et jamais en ligne : le décrément de stock a lieu dans la transaction de paiement, qui
// appelle déjà Stripe — y ajouter un envoi d'e-mail par suiveur ferait expirer le checkout d'un
// acheteur pour prévenir les autres. Et une réservation abandonnée revient au catalogue sous 24 h,
// donc une alerte immédiate serait souvent fausse. Le balayage lit la vérité.
@Component
@RequiredArgsConstructor
public class FavoriteAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(FavoriteAlertScheduler.class);
    private static final long HOURLY_MS = 60 * 60 * 1000L;

    // « Il n'en reste qu'un » au sens littéral. Le seuil est plus strict que l'indice visuel de la
    // boutique (2 ou 3 selon l'écran) pour que l'alerte reste rare, donc crédible.
    private static final long LOW_STOCK_THRESHOLD = 1;

    // Une baisse doit être à la fois sensible en valeur et en proportion : un seuil plat seul est du
    // bruit sur un manteau à 400 €, un pourcentage seul en est sur une pièce à 20 €.
    private static final int PRICE_DROP_MIN_CENTS = 200;
    private static final int PRICE_DROP_MAX_RATIO_PERCENT = 95;

    // Soupape : un catalogue pathologique ne doit pas saturer l'envoi d'e-mails en un passage.
    private static final int MAX_ALERTS_PER_RUN = 500;

    private final MarketplaceFavoriteRepository favoriteRepository;
    private final FavoriteAlertRecorder recorder;
    private final PayableSellerResolver payableSellerResolver;

    @Scheduled(fixedDelay = HOURLY_MS, initialDelay = HOURLY_MS / 2)
    public void sweep() {
        recorder.resetLowStockFlags(LOW_STOCK_THRESHOLD);
        int lowStock = notifyAll(favoriteRepository.findLowStockCandidates(LOW_STOCK_THRESHOLD),
                recorder::sendLowStock);
        int priceDrop = notifyAll(
                favoriteRepository.findPriceDropCandidates(PRICE_DROP_MIN_CENTS, PRICE_DROP_MAX_RATIO_PERCENT),
                recorder::sendPriceDrop);
        if (lowStock > 0 || priceDrop > 0) {
            log.info("Alertes favoris : {} rupture(s) imminente(s), {} baisse(s) de prix", lowStock, priceDrop);
        }
    }

    // Une baisse annoncée sur une pièce que personne ne peut acheter est un cul-de-sac : on écarte
    // les ateliers non encaissables avant d'écrire quoi que ce soit.
    private int notifyAll(List<MarketplaceFavorite> candidates, Predicate<MarketplaceFavorite> send) {
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<UUID> sellerIds = candidates.stream()
                .map(f -> f.getProduct().getArtisanProfile().getUser().getId())
                .collect(Collectors.toSet());
        Set<UUID> payable = payableSellerResolver.payableUserIds(sellerIds);

        int sent = 0;
        for (MarketplaceFavorite favorite : candidates) {
            if (sent >= MAX_ALERTS_PER_RUN) {
                log.warn("Plafond d'alertes favoris atteint ({}) : le reste part au prochain passage",
                        MAX_ALERTS_PER_RUN);
                break;
            }
            if (!payable.contains(favorite.getProduct().getArtisanProfile().getUser().getId())) {
                continue;
            }
            if (send.test(favorite)) {
                sent++;
            }
        }
        return sent;
    }
}
