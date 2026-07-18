package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minoh.lumiris_backend.entity.ArtisanStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArtisanProfileResponse(
        UUID id,
        String userEmail,
        String userName,
        ArtisanStatus status,
        String siret,
        String companyName,
        String nafCode,
        boolean declarationSigned,
        Instant signatureTimestamp,
        String rejectionReason,
        Instant createdAt,

        String slug,
        boolean published,
        String atelierName,
        String story,
        String method,
        String journey,
        List<String> specialties,
        String city,
        String region,
        String websiteUrl,
        Map<String, String> links,
        List<ArtisanPhotoResponse> photos
) {}
