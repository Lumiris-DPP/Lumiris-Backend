package com.minoh.lumiris_backend.exception;

// Webhook payload failed signature verification → HTTP 400.
public class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException(String message) {
        super(message);
    }
}
