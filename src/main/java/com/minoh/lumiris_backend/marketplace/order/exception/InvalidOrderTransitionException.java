package com.minoh.lumiris_backend.marketplace.order.exception;

import com.minoh.lumiris_backend.exception.BillingValidationException;

/** Signale une transition interdite par l'état courant de la commande. */
public class InvalidOrderTransitionException extends BillingValidationException {
    /** Conserve le message métier et la réponse de validation existante. */
    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
