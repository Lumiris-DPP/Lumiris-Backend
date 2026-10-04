package com.minoh.lumiris_backend.integration.stripe;

import com.minoh.lumiris_backend.exception.BillingException;
import com.stripe.exception.StripeException;

// Traduit les erreurs Stripe en erreurs de paiement.
public final class StripeCalls {

    @FunctionalInterface
    public interface StripeOp<T> {

        T execute() throws StripeException;
    }

    // Empêche la création d'une instance de cet outil.
    private StripeCalls() {
    }

    // Convertit un échec Stripe en erreur de paiement explicite.
    public static <T> T billed(String failureMessage, StripeOp<T> op) {
        try {
            return op.execute();
        } catch (StripeException e) {
            throw new BillingException(failureMessage + ": " + e.getMessage(), e);
        }
    }
}
