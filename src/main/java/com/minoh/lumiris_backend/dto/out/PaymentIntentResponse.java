package com.minoh.lumiris_backend.dto.out;

// Secret client d'un PaymentIntent Connect, pour confirmer le paiement via Stripe Payment Element
// (paiement embarqué dans l'UI VISION, sans redirection). Commission = part plateforme (~5%).
public record PaymentIntentResponse(
        String clientSecret,
        String publishableKey,
        int amountTotalCents,
        int commissionCents
) {}
