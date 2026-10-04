package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Présente une pièce avec son atelier et ses déclinaisons.
public record MarketplaceItemResponse(
        UUID id,
        UUID artisanProfileId,
        String artisanName,
        UUID dppFormId,
        String name,
        String description,
        String category,
        String material,
        String originCountry,
        int priceCents,
        String currency,

        int stock,
        List<ProductVariantResponse> variants,
        List<SizeMeasurementResponse> sizeGuide,

        int shippingCents,
        String returnPolicy,
        String warrantyDescription,

        int preparationDays,
        int effectivePreparationDays,
        Instant atelierPausedUntil,

        int weightGrams,
        String externalOrderUrl,
        String photoUrl,
        MarketplaceProductStatus status,
        Double irisTotal,
        String irisGrade,
        boolean atelierPlus,
        boolean inAppSale,
        Instant createdAt,

        long views,
        long salesCount
) {}
