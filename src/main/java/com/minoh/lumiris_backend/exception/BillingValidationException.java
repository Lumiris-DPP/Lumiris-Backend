package com.minoh.lumiris_backend.exception;

// Client-side billing problem (bad plan, payment method not confirmed, …) → HTTP 422.
// Distinct from BillingException, which signals an upstream Stripe failure (502).
public class BillingValidationException extends RuntimeException {
    public BillingValidationException(String message) {
        super(message);
    }
}
