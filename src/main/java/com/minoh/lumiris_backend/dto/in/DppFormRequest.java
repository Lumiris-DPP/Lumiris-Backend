package com.minoh.lumiris_backend.dto.in;

import java.math.BigDecimal;

public record DppFormRequest(
        String productName,
        String productType,
        String internalReference,
        BigDecimal retailPrice,
        String currency,
        String status
) {}
