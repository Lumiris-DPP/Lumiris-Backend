package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.config.MarketplaceProperties;

// Présente les options de paiement disponibles.
public record PaymentOptionsResponse(
        boolean installmentsEnabled,
        int installmentCount,
        int installmentMinCents
) {

    // Prépare la réponse avec les informations de paiement configurées.
    public static PaymentOptionsResponse from(MarketplaceProperties properties) {
        int count = properties.getInstallmentCount();
        return new PaymentOptionsResponse(
                count > 1,
                count,
                properties.getInstallmentMinCents());
    }
}
