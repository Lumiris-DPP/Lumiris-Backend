package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

import com.minoh.lumiris_backend.marketplace.decision.dto.out.DecisionLogResponse;
import java.util.List;

public record SearchResponse(
        List<MarketplaceItemResponse> items,
        DecisionLogResponse decisionLog
) {}
