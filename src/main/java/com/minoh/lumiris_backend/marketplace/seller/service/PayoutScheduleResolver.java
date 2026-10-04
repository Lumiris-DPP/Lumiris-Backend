package com.minoh.lumiris_backend.marketplace.seller.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.PayoutExpectation;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayoutScheduleResolver {

    private final MarketplaceProperties properties;

    public record PayoutForecast(PayoutExpectation expectation, Instant expectedAt) {}

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

    private PayoutForecast scheduled(Instant origin) {
        return origin == null
                ? new PayoutForecast(PayoutExpectation.IMMINENT, null)
                : new PayoutForecast(PayoutExpectation.SCHEDULED, origin.plus(properties.autoDeliverDelay()));
    }

    private static Instant firstNonNull(Instant... candidates) {
        for (Instant candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}
