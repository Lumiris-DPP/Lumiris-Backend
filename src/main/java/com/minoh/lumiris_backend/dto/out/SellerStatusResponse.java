package com.minoh.lumiris_backend.dto.out;

// État du compte vendeur Stripe Connect d'un artisan (pour piloter l'UI d'onboarding ATELIER).
public record SellerStatusResponse(
        boolean hasAccount,
        boolean onboardingCompleted,
        boolean chargesEnabled,
        boolean payoutsEnabled
) {
    public static SellerStatusResponse none() {
        return new SellerStatusResponse(false, false, false, false);
    }
}
