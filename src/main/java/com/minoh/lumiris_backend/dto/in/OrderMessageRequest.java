package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Texte libre attaché à une commande : message du fil, motif de litige, motif d'annulation.
// `fileIds` porte les preuves déjà téléversées (POST /api/files) — une photo vaut mieux qu'un
// paragraphe pour un article abîmé, et c'est ce sur quoi la plateforme arbitre.
public record OrderMessageRequest(
        @NotBlank @Size(max = 2000) String reason,
        List<UUID> fileIds
) {
    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
