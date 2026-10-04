package com.minoh.lumiris_backend.marketplace.order.scheduling;

import com.minoh.lumiris_backend.marketplace.order.service.OrderLifecycleService;
import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderActorType;
import com.minoh.lumiris_backend.marketplace.checkout.service.DirectSaleService;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(fixedDelay = HOURLY_MS, initialDelay = HOURLY_MS)
    public void advanceStaleOrders() {
        Instant now = Instant.now();
        settleAbandonedPayments(now.minus(ABANDONED_AFTER));
        advanceLifecycle(now);
    }

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
