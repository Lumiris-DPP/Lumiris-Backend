package com.minoh.lumiris_backend.marketplace.shipping.dto.out;

import com.minoh.lumiris_backend.entity.ArtisanProfile;

// Présente l'adresse d'expédition enregistrée par l'atelier.
public record ShipFromAddressResponse(
        String line1,
        String line2,
        String postalCode,
        String city,
        String country,
        String phone,
        boolean complete
) {

    // Prépare la réponse avec les informations de l'adresse atelier enregistrée.
    public static ShipFromAddressResponse from(ArtisanProfile profile) {
        return new ShipFromAddressResponse(
                profile.getShipFromLine1(),
                profile.getShipFromLine2(),
                profile.getShipFromPostalCode(),
                profile.getShipFromCity(),
                profile.getShipFromCountry(),
                profile.getShipFromPhone(),
                profile.hasShipFromAddress());
    }
}
