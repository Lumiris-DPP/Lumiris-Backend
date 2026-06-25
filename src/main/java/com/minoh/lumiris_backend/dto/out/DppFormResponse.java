package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.dto.in.CertificationRequest;
import com.minoh.lumiris_backend.dto.in.MaterialRequest;
import com.minoh.lumiris_backend.entity.DppStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DppFormResponse(
        UUID id,
        Instant createdAt,
        DppStatus status,

        String productName,
        String productDescription,
        String productCategory,
        String originCountry,
        List<String> availableSizes,
        List<String> colors,
        String mainPhotoUrl,

        List<MaterialRequest> materials,
        List<String> careInstructions,
        List<CertificationRequest> certifications,

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
