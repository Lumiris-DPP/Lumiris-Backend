package com.minoh.lumiris_backend.marketplace.order.exception;

import com.minoh.lumiris_backend.exception.BillingValidationException;

public class InvalidOrderTransitionException extends BillingValidationException {

    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
