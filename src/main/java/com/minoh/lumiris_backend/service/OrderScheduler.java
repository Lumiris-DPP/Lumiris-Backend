package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderActorType;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Fait avancer les commandes que plus personne ne touche. Sans ce balayage, chaque parcours a son
// cul-de-sac : un acheteur qui ne confirme jamais bloque les fonds du vendeur, un retour refusé
// reste à vie dans l'onglet « Retours », un panier abandonné garde son stock réservé, et un
// versement échoué laisse l'argent chez la plateforme sans que personne ne le sache.
//
// Une commande en litige n'avance JAMAIS toute seule : seule une décision humaine la débloque.
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);
    private static final long HOURLY_MS = 60 * 60 * 1000L;

    // Un paiement abandonné garde le stock réservé : au-delà d'une journée, la pièce doit revenir
    // au catalogue. Marge large — un paiement en cours de validation bancaire n'est pas abandonné.
    private static final Duration ABANDONED_AFTER = Duration.ofHours(24);

    // Délai au-delà duquel une branche retour laissée sans suite se clôt d'elle-même.
    private static final Duration RETURN_SETTLED_AFTER = Duration.ofDays(14);

    // Relance du vendeur sur une commande payée jamais expédiée. Trois jours : assez pour un
    // atelier qui fabrique à la commande, assez tôt pour éviter que l'acheteur s'inquiète seul.
    private static final Duration UNSHIPPED_REMINDER_AFTER = Duration.ofDays(3);

    private final MarketplaceOrderRepository orderRepository;
    private final OrderLifecycleService lifecycleService;
    private final MarketplaceProperties properties;

    @Scheduled(fixedDelay = HOURLY_MS, initialDelay = HOURLY_MS)
    @Transactional
    public void advanceStaleOrders() {
        Instant now = Instant.now();

        for (MarketplaceOrder order : orderRepository.findAbandonedPending(now.minus(ABANDONED_AFTER))) {
            lifecycleService.cancelAbandoned(order);
        }

        List<MarketplaceOrder> toDeliver = orderRepository.findStaleShipped(
                now.minus(properties.autoDeliverDelay()));
        for (MarketplaceOrder order : toDeliver) {
            lifecycleService.markDelivered(order, OrderActorType.SYSTEM);
        }

        List<MarketplaceOrder> toComplete = orderRepository.findStaleDelivered(
                now.minus(properties.autoCompleteDelay()));
        for (MarketplaceOrder order : toComplete) {
            lifecycleService.complete(order);
        }

        // Retour refusé jamais contesté, ou colis retour réceptionné jamais remboursé : la
        // commande se clôt, ce qui vide l'onglet « Retours » et donne une fin à l'acheteur.
        List<MarketplaceOrder> staleReturns = orderRepository.findStaleReturns(now.minus(RETURN_SETTLED_AFTER));
        for (MarketplaceOrder order : staleReturns) {
            lifecycleService.complete(order);
        }

        if (!toDeliver.isEmpty() || !toComplete.isEmpty() || !staleReturns.isEmpty()) {
            log.info("Échéances commandes : {} présumée(s) livrée(s), {} clôturée(s), {} retour(s) soldé(s)",
                    toDeliver.size(), toComplete.size(), staleReturns.size());
        }
    }

    // Relance quotidienne des commandes payées non expédiées, et reprise des versements en échec.
    // Séparé du balayage horaire : ces deux actions ne doivent pas se répéter toutes les heures.
    @Scheduled(fixedDelay = 24 * HOURLY_MS, initialDelay = 2 * HOURLY_MS)
    @Transactional
    public void remindAndRetry() {
        Instant threshold = Instant.now().minus(UNSHIPPED_REMINDER_AFTER);
        for (MarketplaceOrder order : orderRepository.findUnshippedSince(threshold)) {
            lifecycleService.remindSellerToShip(order);
        }
        for (MarketplaceOrder order : orderRepository.findUnreleased()) {
            lifecycleService.retryRelease(order);
        }
    }
}
