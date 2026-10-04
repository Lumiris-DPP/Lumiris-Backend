package com.minoh.lumiris_backend.marketplace.seller.dto.out;

// Présente le bilan des ventes et des annonces.
public record SellerStatsResponse(
        long salesCount,
        long grossCents,
        long commissionCents,
        long netCents,
        long wardrobeCount,
        long totalViews,
        long productCount,
        long publishedCount
) {}
