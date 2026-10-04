package com.minoh.lumiris_backend.marketplace.order.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DisputeResolutionRequest(
        @NotBlank @Size(max = 2000) String resolution,
        @Positive Integer refundCents
) {}
