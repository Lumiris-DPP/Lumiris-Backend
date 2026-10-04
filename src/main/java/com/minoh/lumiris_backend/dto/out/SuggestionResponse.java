package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.dto.out.DecisionLogResponse;
import java.util.List;

// Présente les pièces suggérées et leur justification.
public record SuggestionResponse(
        List<Suggestion> suggestions,
        DecisionLogResponse decisionLog
) {

    // Associe une pièce suggérée à sa justification.
    public record Suggestion(
            MarketplaceItemResponse item,
            int rank,
            String reason
    ) {}
}
