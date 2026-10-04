package com.minoh.lumiris_backend.marketplace.shipping.dto;

import com.minoh.lumiris_backend.entity.TrackingStatus;
import java.time.Instant;

public record CarrierEvent(
        String parcelId,
        String trackingNumber,
        String trackingUrl,
        TrackingStatus status,
        String label,
        Instant occurredAt
) {}
