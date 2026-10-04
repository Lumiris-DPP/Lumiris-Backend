package com.minoh.lumiris_backend.marketplace.decision.dto.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DecisionLogResponse(
        UUID id,
        String context,
        String sortKey,
        boolean commissionConsidered,
        Instant createdAt,
        List<Entry> ranked
) {

    public record Entry(
            int rank,
            UUID productId,
            String name,
            Double irisTotal,
            boolean atelierPlus,
            String reason
    ) {}
}
