package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.config.MarketplaceProperties;

// Ce que l'acheteur peut savoir du paiement AVANT d'arriver au paiement. Aujourd'hui Stripe
// propose déjà le fractionné (Klarna) dans le Payment Element, mais rien ne l'annonce sur la fiche
// ni dans le panier : la mensualité se découvre au dernier écran, une fois la décision prise ou
// abandonnée. Ces valeurs ne pilotent QUE l'affichage — Lumiris ne prête rien et ne porte aucun
// risque de crédit.
public record PaymentOptionsResponse(
        boolean installmentsEnabled,
        int installmentCount,
        int installmentMinCents
) {
    public static PaymentOptionsResponse from(MarketplaceProperties properties) {
        int count = properties.getInstallmentCount();
        return new PaymentOptionsResponse(
                count > 1,
                count,
                properties.getInstallmentMinCents());
    }
}
