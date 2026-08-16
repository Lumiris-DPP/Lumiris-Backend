package com.minoh.lumiris_backend.dto.out;

import java.util.UUID;

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
