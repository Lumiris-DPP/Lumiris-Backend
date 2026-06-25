package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.DppStatus;

import java.time.Instant;
import java.util.UUID;

public record DppFormSummaryResponse(
        UUID id,
        Instant createdAt,
        DppStatus status,
        String productName,
        String productCategory,
        String sku
) {}
