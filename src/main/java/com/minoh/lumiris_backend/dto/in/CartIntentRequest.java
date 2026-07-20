package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// Panier envoyé pour créer le PaymentIntent Connect (paiement embarqué in-app). Un seul vendeur
// par panier (contrainte des destination charges) — vérifié côté service.
public record CartIntentRequest(
        @NotEmpty @Valid List<Line> items
) {
    public record Line(
            @NotNull UUID productId,
            @Min(1) int quantity
    ) {}
}
