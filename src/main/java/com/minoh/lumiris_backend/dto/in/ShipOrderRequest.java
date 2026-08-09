package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Saisie du suivi d'expédition par le vendeur. Le numéro est obligatoire : sans lui l'acheteur
// n'a rien à suivre, et l'expédition ne serait qu'une case cochée.
public record ShipOrderRequest(
        @NotBlank @Size(max = 80) String carrier,
        @NotBlank @Size(max = 120) String trackingNumber,
        @Size(max = 500) String trackingUrl
) {}
