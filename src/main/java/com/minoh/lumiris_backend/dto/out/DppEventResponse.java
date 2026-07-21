package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.DppEventActorType;

import java.time.Instant;
import java.util.UUID;

public record DppEventResponse(
        UUID id,
        Instant occurredAt,
        String description,
        DppEventActorType actorType,
        String locationCity,
        String locationCountry,
        Double latitude,
        Double longitude,
        Instant createdAt
) {}
