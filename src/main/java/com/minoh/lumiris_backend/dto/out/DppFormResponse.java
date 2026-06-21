package com.minoh.lumiris_backend.dto.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DppFormResponse(
        UUID id,
        String productName,
        String productType,
        String internalReference,
        BigDecimal retailPrice,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
