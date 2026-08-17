package com.minoh.lumiris_backend.service.shipping;

// Bordereau fabriqué : le PDF à imprimer, et le suivi qui n'a plus à être saisi à la main.
// `parcelId` est l'identifiant du colis chez l'agrégateur — la clé par laquelle ses webhooks de
// suivi retrouveront la commande.
public record ShippingLabel(
        String parcelId,
        String carrier,
        String trackingNumber,
        String trackingUrl,
        byte[] pdf
) {}
