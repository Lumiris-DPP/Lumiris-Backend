package com.minoh.lumiris_backend.domain;

public enum BillingCycle {
    MONTHLY("monthly", "month"),
    ANNUAL("annual", "year");

    private final String key;
    private final String stripeInterval;

    BillingCycle(String key, String stripeInterval) {
        this.key = key;
        this.stripeInterval = stripeInterval;
    }

    public String key() {
        return key;
    }

    public String stripeInterval() {
        return stripeInterval;
    }

    public static BillingCycle fromKey(String value) {
        if (value == null) {
            return MONTHLY;
        }
        for (BillingCycle cycle : values()) {
            if (cycle.key.equalsIgnoreCase(value) || cycle.name().equalsIgnoreCase(value)) {
                return cycle;
            }
        }
        return MONTHLY;
    }

    public static BillingCycle fromStripeInterval(String interval) {
        return "year".equalsIgnoreCase(interval) ? ANNUAL : MONTHLY;
    }
}
