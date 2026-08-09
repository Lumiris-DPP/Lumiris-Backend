package com.minoh.lumiris_backend.dto.out;

import java.util.List;

// Secret client du PaymentIntent + clé publiable (Stripe.js). `amountTotalCents` = articles + port
// de tous les ateliers du panier ; `shipments` détaille le port retenu par atelier pour que le
// récapitulatif affiche exactement ce qui est débité.
public record PaymentIntentResponse(
        String clientSecret,
        String publishableKey,
        int amountTotalCents,
        int itemsTotalCents,
        int shippingTotalCents,
        int commissionCents,
        List<Shipment> shipments
) {
    public record Shipment(String sellerName, int itemCount, int shippingCents) {}
}
