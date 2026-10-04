package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Porte les informations de suivi d'une expédition.
public record ShipOrderRequest(
        @NotBlank @Size(max = 80) String carrier,
        @NotBlank @Size(max = 120) String trackingNumber,
        @Size(max = 500) String trackingUrl
) {}
