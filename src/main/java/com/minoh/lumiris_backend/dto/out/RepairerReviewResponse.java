package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.UUID;

public record RepairerReviewResponse(
        UUID id,
        int rating,
        String comment,
        String reviewerName,
        // true si l'avis est rattaché à une intervention terminée (« avis vérifié »).
        boolean verified,
        Instant createdAt
) {}
