package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.exception.BillingException;
import com.stripe.exception.StripeException;

// Runs a Stripe SDK call and turns any StripeException into a BillingException with a contextual message.
// Keeps the "call Stripe, wrap failures" boilerplate in one place.
final class StripeCalls {

    @FunctionalInterface
    interface StripeOp<T> {
        T execute() throws StripeException;
    }

    private StripeCalls() {
    }

    static <T> T billed(String failureMessage, StripeOp<T> op) {
        try {
            return op.execute();
        } catch (StripeException e) {
            throw new BillingException(failureMessage + ": " + e.getMessage(), e);
        }
    }
}
