package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderActorType;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.service.stripe.DirectSaleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Avance les commandes échues et reprend les paiements et reversements. */
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);
    private static final long HOURLY_MS = 60 * 60 * 1000L;
    private static final Duration ABANDONED_AFTER = Duration.ofHours(24);
    private static final Duration RETURN_SETTLED_AFTER = Duration.ofDays(14);
    private static final Duration UNSHIPPED_REMINDER_AFTER = Duration.ofDays(3);

    private final MarketplaceOrderRepository orderRepository;
    private final OrderLifecycleService lifecycleService;
    private final DirectSaleService directSaleService;
    private final MarketplaceProperties properties;

    /** Règle les paiements abandonnés puis avance les échéances des commandes. */
    @Scheduled(fixedDelay = HOURLY_MS, initialDelay = HOURLY_MS)
    public void advanceStaleOrders() {
        Instant now = Instant.now();
        settleAbandonedPayments(now.minus(ABANDONED_AFTER));
        advanceLifecycle(now);
    }

    /** Règle chaque paiement isolément sans bloquer les suivants en cas d’échec. */
    private void settleAbandonedPayments(Instant abandonedBefore) {
        for (String paymentIntentId : orderRepository.findAbandonedPendingPaymentIntents(abandonedBefore)) {
            try {
                directSaleService.settlePendingPayment(paymentIntentId);
            } catch (RuntimeException e) {
                log.error("Paiement abandonné {} non réglé ({}), nouvel essai au prochain balayage",
                        paymentIntentId, e.getClass().getSimpleName());
            }
        }
    }

    /** Délègue chaque échéance au cycle de vie dans sa propre transaction. */
    private void advanceLifecycle(Instant now) {
        for (MarketplaceOrder order : orderRepository.findAbandonedPendingWithoutPaymentIntent(
                now.minus(ABANDONED_AFTER))) {
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
        List<MarketplaceOrder> staleReturns = orderRepository.findStaleReturns(now.minus(RETURN_SETTLED_AFTER));
        for (MarketplaceOrder order : staleReturns) {
            lifecycleService.complete(order);
        }

        if (!toDeliver.isEmpty() || !toComplete.isEmpty() || !staleReturns.isEmpty()) {
            log.info("Échéances commandes : {} présumée(s) livrée(s), {} clôturée(s), {} retour(s) soldé(s)",
                    toDeliver.size(), toComplete.size(), staleReturns.size());
        }
    }

    /** Relance les expéditions en retard et les reversements manquants. */
    @Scheduled(fixedDelay = 24 * HOURLY_MS, initialDelay = 2 * HOURLY_MS)
    public void remindAndRetry() {
        Instant threshold = Instant.now().minus(UNSHIPPED_REMINDER_AFTER);
        for (MarketplaceOrder order : orderRepository.findOverdueUnshipped(threshold)) {
            lifecycleService.remindSellerToShip(order);
        }
        for (MarketplaceOrder order : orderRepository.findUnreleased()) {
            lifecycleService.retryRelease(order);
        }
    }
}
