package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.WardrobeItemKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WardrobeSyncRequest(
        @NotNull @Size(max = 500) List<@Valid Upsert> upserts,
        @NotNull @Size(max = 500) List<@NotBlank @Size(max = 255) String> deletedKeys
) {
    public record Upsert(
            @NotBlank @Size(max = 255) String clientKey,
            @NotNull WardrobeItemKind kind,
            @NotNull Instant addedAt,
            @NotNull Map<String, Object> payload
    ) {}
}

