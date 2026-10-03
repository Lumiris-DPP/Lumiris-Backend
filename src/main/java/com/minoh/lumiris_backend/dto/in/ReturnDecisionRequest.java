package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Décrit les données de ReturnDecisionRequest pour les commandes. */
public record ReturnDecisionRequest(
        boolean accepted,
        @Size(max = 2000) String note,
        List<UUID> fileIds
) {

    /** Renvoie les pièces jointes ou une liste vide. */
    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
