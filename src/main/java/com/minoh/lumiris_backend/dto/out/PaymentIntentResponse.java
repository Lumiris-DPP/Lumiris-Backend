package com.minoh.lumiris_backend.dto.out;

import java.util.List;

// Présente le paiement préparé et les frais du panier.
public record PaymentIntentResponse(
        String clientSecret,
        String publishableKey,
        int amountTotalCents,
        int itemsTotalCents,
        int shippingTotalCents,
        int commissionCents,
        List<Shipment> shipments
) {

    // Présente les frais et le délai d'un atelier.
    public record Shipment(String sellerName, int itemCount, int shippingCents, int preparationDays) {}
}
