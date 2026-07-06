package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.service.QuotaService;

// limit/remaining are null when unlimited.
public record QuotaResponse(
        boolean hasActiveSubscription,
        String tier,
        long used,
        Integer limit,
        Integer remaining,
        boolean unlimited,
        boolean canCreate,
        String reason
) {
    public static QuotaResponse from(QuotaService.Quota q) {
        boolean noLimit = q.unlimited() || q.limit() == null;
        Integer remaining = noLimit ? null : (int) Math.max(0L, q.limit() - q.used());
        return new QuotaResponse(
                q.hasActiveSubscription(),
                q.tier() != null ? q.tier().key() : null,
                q.used(),
                q.limit(),
                remaining,
                q.unlimited(),
                q.canCreate(),
                q.reason()
        );
    }
}
