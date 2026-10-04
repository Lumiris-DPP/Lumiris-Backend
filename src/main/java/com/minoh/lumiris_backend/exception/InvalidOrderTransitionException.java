package com.minoh.lumiris_backend.exception;

import com.minoh.lumiris_backend.exception.BillingValidationException;

// Signale une action incompatible avec l'état de la commande.
public class InvalidOrderTransitionException extends BillingValidationException {

    // Transmet le motif de l'action refusée sur la commande.
    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
