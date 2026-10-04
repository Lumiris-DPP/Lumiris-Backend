package com.minoh.lumiris_backend.marketplace.shipping.dto;

// Regroupe les informations nécessaires à l'envoi d'un colis.
public record ParcelRequest(
        Address from,
        Address to,
        String recipientEmail,
        int weightGrams,
        String reference
) {

    // Porte les coordonnées nécessaires au transporteur.
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
