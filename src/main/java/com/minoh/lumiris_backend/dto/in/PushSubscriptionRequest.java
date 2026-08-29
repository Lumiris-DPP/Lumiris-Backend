package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Forme exacte de PushSubscription.toJSON() côté navigateur — pas de reshape front nécessaire.
public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {
    }
}
