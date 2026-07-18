package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minoh.lumiris_backend.entity.ArtisanStatus;

import java.time.Instant;
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
        Instant createdAt
) {}
