package com.minoh.lumiris_backend.dto.out;

import java.util.UUID;

// Présente une déclinaison et son stock disponible.
public record ProductVariantResponse(
        UUID id,
        String sizeLabel,
        String colorLabel,
        String colorHex,
        String sku,
        int stock,
        int position,
        long version
) {}
