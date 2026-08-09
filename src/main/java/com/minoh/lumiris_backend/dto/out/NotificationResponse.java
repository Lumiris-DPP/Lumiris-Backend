package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String href,
        UUID orderId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getBody(),
                n.getHref(),
                n.getOrder() != null ? n.getOrder().getId() : null,
                n.getReadAt() != null,
                n.getCreatedAt()
        );
    }
}
