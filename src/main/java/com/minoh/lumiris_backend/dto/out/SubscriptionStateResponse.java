package com.minoh.lumiris_backend.dto.out;

public record SubscriptionStateResponse(
        SubscriptionResponse subscription,
        QuotaResponse quota,
        boolean hasActiveSubscription,
        boolean hasLiveSubscription,
        String publishableKey
) {}
