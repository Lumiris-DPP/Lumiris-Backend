package com.minoh.lumiris_backend.domain;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;

// Passport quota: Solo 50, Studio 300, Maison unlimited. ATELIER+/Local grant no passports.
public enum PlanTier {

    ATELIER_SOLO("solo", "ATELIER Solo", 2_900L, 29_000L, 50, true),
    ATELIER_STUDIO("studio", "ATELIER Studio", 7_900L, 79_000L, 300, true),
    ATELIER_MAISON("maison", "ATELIER Maison", 14_900L, 149_000L, null, true),
    ATELIER_PLUS("atelier-plus", "ATELIER+", 1_900L, 19_000L, 0, false),
    LOCAL("local", "LUMIRIS Local", 1_900L, 19_000L, 0, false);

    private final String key;
    private final String displayName;
    private final long monthlyAmountCents;
    private final long annualAmountCents;
    // null means unlimited (only meaningful when granting).
    private final Integer passportQuota;
    private final boolean grantsPassports;

    PlanTier(String key, String displayName, long monthlyAmountCents, long annualAmountCents,
             Integer passportQuota, boolean grantsPassports) {
        this.key = key;
        this.displayName = displayName;
        this.monthlyAmountCents = monthlyAmountCents;
        this.annualAmountCents = annualAmountCents;
        this.passportQuota = passportQuota;
        this.grantsPassports = grantsPassports;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean grantsPassports() {
        return grantsPassports;
    }

    // Wire/DTO representation of the quota: 0 = grants none, null = unlimited, N = finite cap.
    // For business logic prefer passportLimit()/isUnlimited(), which avoid the "null = unlimited" trap.
    public Integer passportQuota() {
        if (!grantsPassports) {
            return 0;
        }
        return passportQuota;
    }

    public boolean isUnlimited() {
        return grantsPassports && passportQuota == null;
    }

    // The finite passport cap, if any. Empty = unlimited, or a tier that grants no passports at all.
    // Gate on grantsPassports()/isUnlimited() first; within a granting, non-unlimited tier this is always present.
    public OptionalInt passportLimit() {
        if (!grantsPassports || passportQuota == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(passportQuota);
    }

    public long monthlyAmountCents() {
        return monthlyAmountCents;
    }

    public long annualAmountCents() {
        return annualAmountCents;
    }

    public long amountCents(BillingCycle cycle) {
        return cycle == BillingCycle.ANNUAL ? annualAmountCents : monthlyAmountCents;
    }

    public String lookupKey(BillingCycle cycle) {
        return "lumiris_" + key.replace('-', '_') + "_" + cycle.key();
    }

    public String productId(StripeProperties.Products products) {
        return switch (this) {
            case ATELIER_SOLO -> products.solo();
            case ATELIER_STUDIO -> products.studio();
            case ATELIER_MAISON -> products.maison();
            case ATELIER_PLUS -> products.atelierPlus();
            case LOCAL -> products.local();
        };
    }

    public static Optional<PlanTier> fromKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.key.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value))
                .findFirst();
    }

    public static Optional<PlanTier> fromProductId(String productId, StripeProperties.Products products) {
        if (productId == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> productId.equals(t.productId(products)))
                .findFirst();
    }
}
