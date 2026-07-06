package com.minoh.lumiris_backend.exception;

// Billing is unavailable because Stripe is not configured (no secret key) → HTTP 503.
public class StripeNotConfiguredException extends RuntimeException {
    public StripeNotConfiguredException(String message) {
        super(message);
    }
}
