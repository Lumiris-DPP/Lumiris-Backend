package com.minoh.lumiris_backend.marketplace.shipping.dto;

// Regroupe le document et les références d'un envoi.
public record ShippingLabel(
        String parcelId,
        String carrier,
        String trackingNumber,
        String trackingUrl,
        byte[] pdf
) {}
