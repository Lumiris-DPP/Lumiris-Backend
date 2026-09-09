package com.minoh.lumiris_backend.dto.out;

// Secret client du PaymentIntent du devis de retouche + clé publiable (Stripe.js / Payment Element).
public record RepairPaymentIntentResponse(
        String clientSecret,
        String publishableKey,
        long amountCents,
        String currency
) {}
