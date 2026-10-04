package com.minoh.lumiris_backend.marketplace.seller.dto.out;

public record SellerEarningsResponse(
        long heldCents,
        long releasedCents,
        String currency
) {}
