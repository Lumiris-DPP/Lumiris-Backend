package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.DppEventActorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;

public record DppEventRequest(
        @NotNull @PastOrPresent Instant occurredAt,
        @NotBlank String description,
        @NotNull DppEventActorType actorType
) {}
