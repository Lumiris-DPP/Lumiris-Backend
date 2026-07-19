package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Log de décision exposé : rend le tri auditable. `ranked` explicite l'ordre retenu
// (rang, score Iris, statut ATELIER+, raison) ; commissionConsidered est toujours false.
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
