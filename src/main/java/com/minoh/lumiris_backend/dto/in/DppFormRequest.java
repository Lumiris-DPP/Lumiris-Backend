package com.minoh.lumiris_backend.dto.in;

import java.util.List;

public record DppFormRequest(
        String productName,
        String productDescription,
        String productCategory,
        String originCountry,
        List<String> availableSizes,
        List<String> colors,

        List<MaterialRequest> materials,
        List<String> careInstructions,
        String careNotes,

        String manufacturedAt,
        String batchNumber,
        String gtin,
        String sku,
        Boolean reachCompliant,

        Integer recycledPct,
        String warrantyDescription,
        Boolean isRepairable,
        String endOfLifeInstructions
) {}
