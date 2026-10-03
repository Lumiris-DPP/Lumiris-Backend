package com.minoh.lumiris_backend.dto.out;

import java.util.UUID;

/** Décrit les données de OrderAttachmentResponse pour les commandes. */
public record OrderAttachmentResponse(
        UUID id,
        String filename,
        String contentType,
        String url
) {}
