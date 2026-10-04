package com.minoh.lumiris_backend.marketplace.shipping.dto;

public record ParcelRequest(
        Address from,
        Address to,
        String recipientEmail,
        int weightGrams,
        String reference
) {

    public record Address(
            String fullName,
            String line1,
            String line2,
            String postalCode,
            String city,
            String country,
            String phone
    ) {}
}
