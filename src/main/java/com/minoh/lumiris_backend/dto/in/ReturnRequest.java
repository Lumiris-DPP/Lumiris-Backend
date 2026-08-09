package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Demande de retour de l'acheteur. Le motif est obligatoire : il conditionne la décision du
// vendeur et constitue la première pièce du dossier en cas de litige. Les photos jointes rendent
// un « article abîmé » vérifiable au lieu d'être une affirmation.
public record ReturnRequest(
        @NotBlank @Size(max = 2000) String reason,
        List<UUID> fileIds
) {
    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
