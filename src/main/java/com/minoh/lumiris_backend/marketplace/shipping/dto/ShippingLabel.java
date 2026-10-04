package com.minoh.lumiris_backend.marketplace.shipping.dto;

public record ShippingLabel(
        String parcelId,
        String carrier,
        String trackingNumber,
        String trackingUrl,
        byte[] pdf
) {}
