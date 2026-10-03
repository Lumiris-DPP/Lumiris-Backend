package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

public record SizeMeasurementResponse(
        String sizeLabel,
        String label,
        int valueMm,
        int position
) {}
