package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Présente les critères et les résultats d'un classement.
public record DecisionLogResponse(
        UUID id,
        String context,
        String sortKey,
        boolean commissionConsidered,
        Instant createdAt,
        List<Entry> ranked
) {

    // Présente la position et la justification d'une pièce.
    public record Entry(
            int rank,
            UUID productId,
            String name,
            Double irisTotal,
            boolean atelierPlus,
            String reason
    ) {}
}
