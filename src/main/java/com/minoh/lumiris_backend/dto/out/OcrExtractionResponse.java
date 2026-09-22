package com.minoh.lumiris_backend.dto.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OcrExtractionResponse(
        String supplierName,
        LocalDate invoiceDate,
        BigDecimal totalHt,
        String currency,
        List<OcrLineItemResponse> lineItems
) {}
