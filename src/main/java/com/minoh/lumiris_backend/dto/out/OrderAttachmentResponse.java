package com.minoh.lumiris_backend.dto.out;

import java.util.UUID;

// Pièce jointe d'un évènement de commande. `url` est présignée et expire : elle sert à afficher
// la preuve, pas à la partager durablement.
public record OrderAttachmentResponse(
        UUID id,
        String filename,
        String contentType,
        String url
) {}
