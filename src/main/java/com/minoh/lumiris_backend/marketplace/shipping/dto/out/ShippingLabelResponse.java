package com.minoh.lumiris_backend.marketplace.shipping.dto.out;

public record ShippingLabelResponse(
        String labelUrl,
        String carrier,
        String trackingNumber,
        String trackingUrl
) {

    public record Availability(boolean enabled, String provider, boolean senderAddressReady) {

        public static Availability unavailable() {
            return new Availability(false, null, false);
        }
    }
}
