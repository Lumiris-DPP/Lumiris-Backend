package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.UUID;

public record CertificateLibraryItemResponse(
        UUID id,
        String type,
        String filename,
        String url,
        long usedOnDppCount,
        Instant createdAt
) {}
