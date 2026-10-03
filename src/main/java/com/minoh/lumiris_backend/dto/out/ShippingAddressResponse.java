package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;

/** Décrit les données de ShippingAddressResponse pour les commandes. */
public record ShippingAddressResponse(
        String fullName,
        String line1,
        String line2,
        String postalCode,
        String city,
        String country,
        String phone
) {

    /** Construit la réponse à partir des données persistantes de la commande. */
    public static ShippingAddressResponse from(MarketplaceOrder o) {
        if (o.getShipToLine1() == null) {
            return null;
        }
        return new ShippingAddressResponse(
                o.getShipToName(),
                o.getShipToLine1(),
                o.getShipToLine2(),
                o.getShipToPostalCode(),
                o.getShipToCity(),
                o.getShipToCountry(),
                o.getShipToPhone()
        );
    }
}
