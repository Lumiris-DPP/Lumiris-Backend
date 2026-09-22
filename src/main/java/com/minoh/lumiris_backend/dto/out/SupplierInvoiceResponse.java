package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.UUID;

public record SupplierInvoiceResponse(
        UUID id,
        String fileUrl,
        OcrExtractionResponse ocrExtracted,
        Instant uploadedAt
) {}
