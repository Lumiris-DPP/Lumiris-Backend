package com.minoh.lumiris_backend.dto.out;

public record SizeMeasurementResponse(
        String sizeLabel,
        String label,
        int valueMm,
        int position
) {}
