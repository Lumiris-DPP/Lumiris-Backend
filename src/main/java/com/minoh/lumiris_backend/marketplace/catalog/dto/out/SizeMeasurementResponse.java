package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

/** Décrit une mesure du guide de tailles de l’annonce. */
public record SizeMeasurementResponse(
        String sizeLabel,
        String label,
        int valueMm,
        int position
) {}
