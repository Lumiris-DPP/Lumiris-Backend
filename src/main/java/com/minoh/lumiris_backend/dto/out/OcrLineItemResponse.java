package com.minoh.lumiris_backend.dto.out;

import java.math.BigDecimal;

public record OcrLineItemResponse(
        String fiber,
        String label,
        BigDecimal qty,
        String unit
) {}
