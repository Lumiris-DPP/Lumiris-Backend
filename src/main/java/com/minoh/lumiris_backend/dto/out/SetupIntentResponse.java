package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.service.stripe.SubscriptionService;

public record SetupIntentResponse(
        String clientSecret,
        String publishableKey,
        String tier,
        String cycle,
        String priceId,
        long amountCents
) {
    public static SetupIntentResponse from(SubscriptionService.SetupIntentResult r) {
        return new SetupIntentResponse(
                r.clientSecret(),
                r.publishableKey(),
                r.tier().key(),
                r.cycle().key(),
                r.priceId(),
                r.amountCents()
        );
    }
}
