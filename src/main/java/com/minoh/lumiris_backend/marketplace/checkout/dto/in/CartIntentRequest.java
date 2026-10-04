package com.minoh.lumiris_backend.marketplace.checkout.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

// Porte les pièces du panier et l'adresse de livraison.
public record CartIntentRequest(
        @NotEmpty @Valid List<Line> items,
        @NotNull @Valid ShippingAddress shipping
) {

    // Désigne une déclinaison et la quantité demandée.
    public record Line(
            @NotNull UUID productId,
            UUID variantId,
            @Min(1) int quantity
    ) {}

    // Porte les coordonnées du destinataire de la commande.
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
