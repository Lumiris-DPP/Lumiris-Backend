package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Porte la décision de la plateforme sur un litige.
public record DisputeResolutionRequest(
        @NotBlank @Size(max = 2000) String resolution,
        @Positive Integer refundCents
) {}
