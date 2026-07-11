package com.minoh.lumiris_backend.dto.out;

import java.util.List;

public record CatalogResponse(
        String publishableKey,
        List<PlanResponse> plans
) {}
