package com.minoh.lumiris_backend.marketplace.shipping.exception;

public class ShippingProviderException extends RuntimeException {

    public ShippingProviderException(String message) {
        super(message);
    }

    public ShippingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
