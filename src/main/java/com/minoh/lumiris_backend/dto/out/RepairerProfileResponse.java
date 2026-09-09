package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minoh.lumiris_backend.entity.RepairerSource;
import com.minoh.lumiris_backend.entity.RepairerStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepairerProfileResponse(
        UUID id,
        String userEmail,
        RepairerStatus status,
        RepairerSource source,
        Instant importedAt,
        Instant claimedAt,
        String siret,
        String companyName,
        String displayName,
        List<String> specialties,
        List<String> zones,
        String schedule,
        String address,
        String city,
        String region,
        Double averageRating,
        long reviewCount,
        Instant createdAt,
        KybDetailsResponse kyb
) {}
