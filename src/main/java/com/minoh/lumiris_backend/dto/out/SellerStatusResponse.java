package com.minoh.lumiris_backend.dto.out;

// Présente l'activation du compte de paiement vendeur.
public record SellerStatusResponse(
        boolean hasAccount,
        boolean onboardingCompleted,
        boolean chargesEnabled,
        boolean payoutsEnabled
) {

    // Présente un compte vendeur qui n'est pas encore créé.
    public static SellerStatusResponse none() {
        return new SellerStatusResponse(false, false, false, false);
    }
}
