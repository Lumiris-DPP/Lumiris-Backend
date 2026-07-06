package com.minoh.lumiris_backend.exception;

// Stripe SDK / billing failures → HTTP 502, code BILLING_ERROR.
public class BillingException extends RuntimeException {
    public BillingException(String message) {
        super(message);
    }

    public BillingException(String message, Throwable cause) {
        super(message, cause);
    }
}
