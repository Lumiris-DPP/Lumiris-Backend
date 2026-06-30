package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.UUID;

public record FileUploadResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {}
