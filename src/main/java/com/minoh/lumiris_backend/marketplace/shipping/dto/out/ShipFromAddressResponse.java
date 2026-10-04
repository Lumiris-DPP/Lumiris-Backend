package com.minoh.lumiris_backend.marketplace.shipping.dto.out;

import com.minoh.lumiris_backend.entity.ArtisanProfile;

public record ShipFromAddressResponse(
        String line1,
        String line2,
        String postalCode,
        String city,
        String country,
        String phone,
        boolean complete
) {

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
