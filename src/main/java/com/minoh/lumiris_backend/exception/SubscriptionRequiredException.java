package com.minoh.lumiris_backend.exception;

// Action needs an active passport-granting subscription → HTTP 403, code SUBSCRIPTION_REQUIRED.
public class SubscriptionRequiredException extends RuntimeException {
    public SubscriptionRequiredException(String message) {
        super(message);
    }
}
