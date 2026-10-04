package com.minoh.lumiris_backend.service.shipping;

import com.minoh.lumiris_backend.entity.TrackingStatus;
import java.time.Instant;

// Porte le suivi reçu pour un colis.
public record CarrierEvent(
        String parcelId,
        String trackingNumber,
        String trackingUrl,
        TrackingStatus status,
        String label,
        Instant occurredAt
) {}
