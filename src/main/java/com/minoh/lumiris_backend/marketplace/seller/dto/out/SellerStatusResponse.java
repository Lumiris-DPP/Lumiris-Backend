package com.minoh.lumiris_backend.marketplace.seller.dto.out;

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
