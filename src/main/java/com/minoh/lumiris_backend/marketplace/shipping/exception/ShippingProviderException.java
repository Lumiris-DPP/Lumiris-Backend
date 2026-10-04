package com.minoh.lumiris_backend.marketplace.shipping.exception;

// Signale un échec du service de transport.
public class ShippingProviderException extends RuntimeException {

    // Conserve le motif de l'échec du transporteur.
    public ShippingProviderException(String message) {
        super(message);
    }

    // Conserve le motif de l'échec du transporteur.
    public ShippingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
