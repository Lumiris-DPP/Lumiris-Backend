package com.minoh.lumiris_backend.marketplace.order.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Décrit les données de ReturnRequest pour les commandes. */
public record ReturnRequest(
        @NotBlank @Size(max = 2000) String reason,
        List<UUID> fileIds
) {

    /** Renvoie les pièces jointes ou une liste vide. */
    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
