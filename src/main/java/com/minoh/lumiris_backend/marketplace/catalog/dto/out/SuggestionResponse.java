package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

import com.minoh.lumiris_backend.marketplace.decision.dto.out.DecisionLogResponse;
import java.util.List;

public record SuggestionResponse(
        List<Suggestion> suggestions,
        DecisionLogResponse decisionLog
) {

    public record Suggestion(
            MarketplaceItemResponse item,
            int rank,
            String reason
    ) {}
}
