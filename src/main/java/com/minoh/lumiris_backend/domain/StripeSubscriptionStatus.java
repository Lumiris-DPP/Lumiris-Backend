package com.minoh.lumiris_backend.domain;

import java.util.Set;

/**
 * Helpers around the raw Stripe subscription status strings, so the literals
 * ("active", "trialing", …) live in one place instead of being scattered.
 */
public final class StripeSubscriptionStatus {

    public static final String ACTIVE = "active";
    public static final String TRIALING = "trialing";
    public static final String PAST_DUE = "past_due";
    public static final String INCOMPLETE = "incomplete";
    public static final String UNPAID = "unpaid";

    // Grants access (passport creation, quota).
    private static final Set<String> ACTIVE_STATES = Set.of(ACTIVE, TRIALING);
    // Still a "live" subscription that blocks creating a second one.
    private static final Set<String> LIVE_STATES = Set.of(ACTIVE, TRIALING, PAST_DUE, INCOMPLETE, UNPAID);

    private StripeSubscriptionStatus() {
    }

    public static boolean isActive(String status) {
        return status != null && ACTIVE_STATES.contains(status);
    }

    public static boolean isLive(String status) {
        return status != null && LIVE_STATES.contains(status);
    }
}
