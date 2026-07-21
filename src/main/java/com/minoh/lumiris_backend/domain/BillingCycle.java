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

    // null → défaut MONTHLY (cas légitime "non précisé"). Une valeur NON reconnue est en revanche
    // rejetée : un front qui envoie "yearly" ne doit pas être facturé silencieusement au mois.
    public static BillingCycle fromKey(String value) {
        if (value == null || value.isBlank()) {
            return MONTHLY;
        }
        for (BillingCycle cycle : values()) {
            if (cycle.key.equalsIgnoreCase(value) || cycle.name().equalsIgnoreCase(value)) {
                return cycle;
            }
        }
        throw new IllegalArgumentException(
                "Cycle de facturation inconnu : « " + value + " » (attendu : monthly | annual).");
    }

    public static BillingCycle fromStripeInterval(String interval) {
        return "year".equalsIgnoreCase(interval) ? ANNUAL : MONTHLY;
    }
}
