package com.minoh.lumiris_backend.marketplace.order.dto.in;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

// Porte la réponse de l'atelier à une demande de retour.
public record ReturnDecisionRequest(
        boolean accepted,
        @Size(max = 2000) String note,
        List<UUID> fileIds
) {

    // Fournit les pièces jointes ou une liste vide.
    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
