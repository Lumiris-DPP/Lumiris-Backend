package com.minoh.lumiris_backend.marketplace.seller.dto.out;

// Présente les fonds retenus et versés à l'atelier.
public record SellerEarningsResponse(
        long heldCents,
        long releasedCents,
        String currency
) {}
