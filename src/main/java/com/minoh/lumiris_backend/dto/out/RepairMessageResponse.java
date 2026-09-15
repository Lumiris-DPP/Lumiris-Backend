package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.UUID;

public record RepairMessageResponse(
        UUID id,
        String senderName,
        boolean fromRepairer,
        String body,
        Instant createdAt
) {}
