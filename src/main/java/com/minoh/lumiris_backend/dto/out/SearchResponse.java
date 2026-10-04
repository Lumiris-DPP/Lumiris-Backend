package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.dto.out.DecisionLogResponse;
import java.util.List;

// Présente les pièces trouvées et la décision de classement.
public record SearchResponse(
        List<MarketplaceItemResponse> items,
        DecisionLogResponse decisionLog
) {}
