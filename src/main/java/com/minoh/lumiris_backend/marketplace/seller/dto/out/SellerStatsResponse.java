package com.minoh.lumiris_backend.marketplace.seller.dto.out;

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
