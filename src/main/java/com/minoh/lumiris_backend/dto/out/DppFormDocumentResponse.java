package com.minoh.lumiris_backend.dto.out;

import java.util.UUID;

public record DppFormDocumentResponse(
        UUID fileId,
        String documentType,
        String visibility,
        String filename,
        String url
) {}
