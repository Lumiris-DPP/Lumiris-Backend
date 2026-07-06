package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.service.stripe.StripeCatalogService;

public record PlanResponse(
        String tier,
        String label,
        String productId,
        String monthlyPriceId,
        String annualPriceId,
        long monthlyAmountCents,
        long annualAmountCents,
        boolean grantsPassports,
        Integer passportQuota,
        boolean unlimited
) {
    public static PlanResponse from(StripeCatalogService.CatalogEntry e) {
        return new PlanResponse(
                e.key(),
                e.displayName(),
                e.productId(),
                e.monthlyPriceId(),
                e.annualPriceId(),
                e.monthlyAmountCents(),
                e.annualAmountCents(),
                e.grantsPassports(),
                e.unlimited() ? null : e.passportQuota(),
                e.unlimited()
        );
    }
}
