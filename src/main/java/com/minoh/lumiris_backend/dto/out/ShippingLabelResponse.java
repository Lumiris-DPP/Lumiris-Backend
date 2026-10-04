package com.minoh.lumiris_backend.dto.out;

// Présente l'étiquette et les références de suivi du colis.
public record ShippingLabelResponse(
        String labelUrl,
        String carrier,
        String trackingNumber,
        String trackingUrl
) {

    // Indique si une étiquette de transport peut être créée.
    public record Availability(boolean enabled, String provider, boolean senderAddressReady) {

        // Indique que la création d'étiquette n'est pas disponible.
        public static Availability unavailable() {
            return new Availability(false, null, false);
        }
    }
}
