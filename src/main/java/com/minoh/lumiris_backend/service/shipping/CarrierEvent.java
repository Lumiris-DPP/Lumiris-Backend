package com.minoh.lumiris_backend.service.shipping;

import com.minoh.lumiris_backend.entity.TrackingStatus;

import java.time.Instant;

// Un événement poussé par le transporteur, ramené au vocabulaire Lumiris. `label` conserve la
// phrase brute de l'agrégateur : lui seul sait dire « disponible au point relais » là où notre
// enum ne connaît que OUT_FOR_DELIVERY, et c'est cette phrase que lit l'acheteur.
public record CarrierEvent(
        String parcelId,
        String trackingNumber,
        String trackingUrl,
        TrackingStatus status,
        String label,
        Instant occurredAt
) {}
