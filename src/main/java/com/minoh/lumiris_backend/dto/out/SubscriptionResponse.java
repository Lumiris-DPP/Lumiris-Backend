package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.UserSubscription;

import java.time.Instant;

public record SubscriptionResponse(
        String tier,
        String tierLabel,
        String billingCycle,
        String status,
        boolean active,
        boolean grantsPassports,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        String priceId
) {
    public static SubscriptionResponse from(UserSubscription s) {
        if (s == null || s.getPlanTier() == null) {
            return null;
        }
        return new SubscriptionResponse(
                s.getPlanTier().key(),
                s.getPlanTier().displayName(),
                s.getBillingCycle() != null ? s.getBillingCycle().key() : null,
                s.getStatus(),
                s.isActive(),
                s.getPlanTier().grantsPassports(),
                s.getCurrentPeriodEnd(),
                s.isCancelAtPeriodEnd(),
                s.getStripePriceId()
        );
    }
}
