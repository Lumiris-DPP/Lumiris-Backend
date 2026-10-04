package com.minoh.lumiris_backend.integration.stripe;

import com.minoh.lumiris_backend.exception.BillingException;
import com.stripe.exception.StripeException;

public final class StripeCalls {

    @FunctionalInterface
    public interface StripeOp<T> {

        T execute() throws StripeException;
    }

    private StripeCalls() {
    }

    public static <T> T billed(String failureMessage, StripeOp<T> op) {
        try {
            return op.execute();
        } catch (StripeException e) {
            throw new BillingException(failureMessage + ": " + e.getMessage(), e);
        }
    }
}
