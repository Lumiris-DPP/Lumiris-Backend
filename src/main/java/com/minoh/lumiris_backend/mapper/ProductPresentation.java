package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.out.ProductVariantResponse;
import com.minoh.lumiris_backend.dto.out.SizeMeasurementResponse;
import java.time.Instant;
import java.util.List;

// Regroupe les informations complémentaires d'une annonce.
public record ProductPresentation(
        List<ProductVariantResponse> variants,
        List<SizeMeasurementResponse> sizeGuide,
        boolean atelierPlus,
        int effectivePreparationDays,
        Instant atelierPausedUntil,
        long salesCount
) {}
