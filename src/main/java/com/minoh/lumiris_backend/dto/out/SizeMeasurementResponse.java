package com.minoh.lumiris_backend.dto.out;

// Présente une mesure du guide des tailles.
public record SizeMeasurementResponse(
        String sizeLabel,
        String label,
        int valueMm,
        int position
) {}
