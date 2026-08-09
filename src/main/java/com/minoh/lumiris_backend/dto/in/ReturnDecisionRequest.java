package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Réponse du vendeur à une demande de retour. `fileIds` permet de joindre l'étiquette de retour
// ou une photo de l'emballage attendu.
public record ReturnDecisionRequest(
        boolean accepted,
        @Size(max = 2000) String note,
        List<UUID> fileIds
) {
    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
