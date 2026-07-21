package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.UUID;

public record RepairerReviewResponse(
        UUID id,
        int rating,
        String comment,
        String reviewerName,
        Instant createdAt
) {}
