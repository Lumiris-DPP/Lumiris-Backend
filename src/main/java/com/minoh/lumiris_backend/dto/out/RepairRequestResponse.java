package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.RepairRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record RepairRequestResponse(
        UUID id,
        UUID repairerProfileId,
        String repairerDisplayName,
        String consumerName,
        UUID dppFormId,
        String dppPublicCode,
        String dppProductName,
        String message,
        RepairRequestStatus status,
        Long quoteAmountCents,
        String quoteDescription,
        Instant quoteSubmittedAt,
        Instant appointmentAt,
        Instant paidAt,
        Instant createdAt
) {}
