package com.minoh.lumiris_backend.dto.out;

import java.util.List;

// Résultat de la recherche catalogue : les produits + le log de décision du tri.
public record SearchResponse(
        List<MarketplaceItemResponse> items,
        DecisionLogResponse decisionLog
) {}
