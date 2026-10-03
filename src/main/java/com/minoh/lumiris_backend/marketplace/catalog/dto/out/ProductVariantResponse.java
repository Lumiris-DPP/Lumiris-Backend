package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

import java.util.UUID;

/** Décrit une déclinaison avec son stock et sa version. */
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
