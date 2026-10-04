package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.OrderEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

// Présente une étape de commande et ses pièces jointes.
public record OrderEventResponse(
        UUID id,
        String type,
        String actorType,
        String message,
        List<OrderAttachmentResponse> attachments,
        Instant createdAt
) {

    // Prépare la réponse avec les informations de l'événement et ses pièces jointes.
    public static OrderEventResponse from(OrderEvent e, Function<UUID, String> presign) {
        return new OrderEventResponse(
                e.getId(),
                e.getType().name(),
                e.getActorType().name(),
                e.getMessage(),
                e.getAttachments().stream()
                        .map(file -> new OrderAttachmentResponse(
                                file.getId(),
                                file.getOriginalFilename(),
                                file.getContentType(),
                                presign.apply(file.getId())))
                        .toList(),
                e.getCreatedAt()
        );
    }
}
