package com.minoh.lumiris_backend.dto.out;

import java.util.UUID;

// Présente une pièce jointe à un événement de commande.
public record OrderAttachmentResponse(
        UUID id,
        String filename,
        String contentType,
        String url
) {}
