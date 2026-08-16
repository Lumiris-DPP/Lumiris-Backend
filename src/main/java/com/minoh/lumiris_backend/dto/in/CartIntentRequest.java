package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Panier envoyé pour créer le PaymentIntent (paiement embarqué in-app). Le panier peut couvrir
// PLUSIEURS ateliers : l'encaissement se fait sur le compte plateforme, puis un reversement par
// atelier (separate charges & transfers). L'adresse est saisie avant le paiement — sans elle le
// vendeur n'a rien pour expédier.
public record CartIntentRequest(
        @NotEmpty @Valid List<Line> items,
        @NotNull @Valid ShippingAddress shipping
) {
    // variantId est délibérément nullable : l'app mobile est exportée en statique et servie depuis
    // un cache navigateur comme depuis un shell Tauri, donc des bundles antérieurs à la feature
    // continuent de tourner plusieurs jours. Le serveur résout la déclinaison unique d'une annonce
    // qui n'en a qu'une, et refuse en 422 une annonce qui en a plusieurs.
    public record Line(
            @NotNull UUID productId,
            UUID variantId,
            @Min(1) int quantity
    ) {}

    public record ShippingAddress(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 300) String line1,
            @Size(max = 300) String line2,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 2) String country,
            @Size(max = 40) String phone
    ) {}
}
