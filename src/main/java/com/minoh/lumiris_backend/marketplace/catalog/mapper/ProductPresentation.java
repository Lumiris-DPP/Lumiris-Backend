package com.minoh.lumiris_backend.marketplace.catalog.mapper;

import com.minoh.lumiris_backend.marketplace.catalog.dto.out.ProductVariantResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.SizeMeasurementResponse;
import java.time.Instant;
import java.util.List;

public record ProductPresentation(
        List<ProductVariantResponse> variants,
        List<SizeMeasurementResponse> sizeGuide,
        boolean atelierPlus,
        int effectivePreparationDays,
        Instant atelierPausedUntil,
        long salesCount
) {}
