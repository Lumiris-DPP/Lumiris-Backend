package com.minoh.lumiris_backend.marketplace.order.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipOrderRequest(
        @NotBlank @Size(max = 80) String carrier,
        @NotBlank @Size(max = 120) String trackingNumber,
        @Size(max = 500) String trackingUrl
) {}
