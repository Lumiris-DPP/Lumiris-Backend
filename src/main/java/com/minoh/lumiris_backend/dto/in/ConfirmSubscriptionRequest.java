package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSubscriptionRequest(
        @NotBlank String setupIntentId
) {}
