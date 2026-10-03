package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

import com.minoh.lumiris_backend.marketplace.decision.dto.out.DecisionLogResponse;
import java.util.List;

// Résultat de la recherche catalogue : les produits + le log de décision du tri.
public record SearchResponse(
        List<MarketplaceItemResponse> items,
        DecisionLogResponse decisionLog
) {}
