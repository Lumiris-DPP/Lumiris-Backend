package com.minoh.lumiris_backend.integration.stripe;

import com.minoh.lumiris_backend.exception.BillingException;
import com.stripe.exception.StripeException;

/** Traduit les erreurs du SDK Stripe en erreurs de facturation contextualisées. */
public final class StripeCalls {

    /** Décrit un appel Stripe pouvant échouer avec une erreur du SDK. */
    @FunctionalInterface
    public interface StripeOp<T> {
        /** Exécute l’appel Stripe en conservant son résultat et son erreur éventuelle. */
        T execute() throws StripeException;
    }

    /** Empêche l’instanciation de ce helper d’appels Stripe. */
    private StripeCalls() {
    }

    /** Ajoute le contexte de facturation à toute erreur renvoyée par Stripe. */
    public static <T> T billed(String failureMessage, StripeOp<T> op) {
        try {
            return op.execute();
        } catch (StripeException e) {
            throw new BillingException(failureMessage + ": " + e.getMessage(), e);
        }
    }
}
