package com.minoh.lumiris_backend.marketplace.seller.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.PayoutExpectation;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Seul endroit où l'on dit quand une commande sera versée. Le front ne re-dérive jamais une date.
//
// DELIVERED ou COMPLETED sans transfert signifie que le versement Stripe a échoué — la libération
// est synchrone à la livraison, et le balayage le rejoue sous 24 h. D'où IMMINENT sans date : on ne
// promet pas un jour qu'on ne connaît pas. RETURN_REFUSED reste dans les ventes réglées mais les
// fonds sont retenus jusqu'à la clôture : ON_HOLD est le seul statut honnête.
@Service
@RequiredArgsConstructor
public class PayoutScheduleResolver {

    private final MarketplaceProperties properties;

    /** Décrit la date et la cause d’attente du reversement. */
    public record PayoutForecast(PayoutExpectation expectation, Instant expectedAt) {}

    /** Calcule l’échéance selon le suivi actuel de la commande. */
    public PayoutForecast forecast(MarketplaceOrder order) {
        if (order.getDisputeStatus() == DisputeStatus.OPEN) {
            return new PayoutForecast(PayoutExpectation.ON_HOLD, null);
        }
        return switch (order.getStatus()) {
            case PAID -> scheduled(firstNonNull(order.getShipDueAt(), order.getCreatedAt()));
            case SHIPPED -> scheduled(firstNonNull(order.getShippedAt(), order.getShipDueAt(), order.getCreatedAt()));
            case DELIVERED, COMPLETED -> new PayoutForecast(PayoutExpectation.IMMINENT, null);
            default -> new PayoutForecast(PayoutExpectation.ON_HOLD, null);
        };
    }

    /** Construit une échéance en appliquant le délai de reversement existant. */
    private PayoutForecast scheduled(Instant origin) {
        return origin == null
                ? new PayoutForecast(PayoutExpectation.IMMINENT, null)
                : new PayoutForecast(PayoutExpectation.SCHEDULED, origin.plus(properties.autoDeliverDelay()));
    }

    /** Retient la première date connue parmi les dates proposées. */
    private static Instant firstNonNull(Instant... candidates) {
        for (Instant candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
