package com.minoh.lumiris_backend.marketplace.favorite.service;

import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import com.minoh.lumiris_backend.marketplace.favorite.repository.MarketplaceFavoriteRepository;
import com.minoh.lumiris_backend.marketplace.seller.service.PayableSellerResolver;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Détecte les changements des pièces ajoutées aux favoris.
@Component
@RequiredArgsConstructor
public class FavoriteAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(FavoriteAlertScheduler.class);
    private static final long HOURLY_MS = 60 * 60 * 1000L;

    private static final long LOW_STOCK_THRESHOLD = 1;

    private static final int PRICE_DROP_MIN_CENTS = 200;
    private static final int PRICE_DROP_MAX_RATIO_PERCENT = 95;

    private static final int MAX_ALERTS_PER_RUN = 500;

    private final MarketplaceFavoriteRepository favoriteRepository;
    private final FavoriteAlertRecorder recorder;
    private final PayableSellerResolver payableSellerResolver;

    // Recherche les baisses de prix et les stocks faibles.
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

    // Traite les alertes de chaque favori sans arrêter les suivants.
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
