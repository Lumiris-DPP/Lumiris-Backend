package com.minoh.lumiris_backend.service.shipping;

// Regroupe le document et les références d'un envoi.
public record ShippingLabel(
        String parcelId,
        String carrier,
        String trackingNumber,
        String trackingUrl,
        byte[] pdf
) {}
