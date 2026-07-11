package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;

public record CreateSetupIntentRequest(
        @NotBlank String tier,
        String cycle
) {}
