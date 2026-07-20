package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AtelierStatsResponse(
        Instant from,
        Instant to,
        Totals totals,
        boolean advancedLocked,
        List<PassportBreakdown> byPassport
) {
    public record Totals(long scans, long views, long suggestionClicks, long conversions) {}

    public record PassportBreakdown(
            UUID dppFormId,
            String publicCode,
            String productName,
            long scans,
            long views,
            long suggestionClicks,
            long conversions
    ) {}
}
