package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

// Porte le motif et les preuves d'une demande de retour.
public record ReturnRequest(
        @NotBlank @Size(max = 2000) String reason,
        List<UUID> fileIds
) {

    // Fournit les pièces jointes ou une liste vide.
    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
