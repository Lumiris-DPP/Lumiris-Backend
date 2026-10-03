package com.minoh.lumiris_backend.marketplace.shipping.exception;

// L'agrégateur d'expédition a refusé ou n'a pas répondu → HTTP 502. L'atelier garde toujours la
// saisie manuelle du suivi : un incident chez le prestataire ne doit jamais bloquer un colis.
public class ShippingProviderException extends RuntimeException {
    /** Conserve le contexte de l’échec du prestataire de livraison. */
    public ShippingProviderException(String message) {
        super(message);
    }

    /** Conserve le contexte de l’échec du prestataire de livraison. */
    public ShippingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
