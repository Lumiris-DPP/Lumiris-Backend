package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.EmailOutbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EmailOutboxResponse(
        UUID id,
        String recipientEmail,
        String subject,
        String template,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        Map<String, Object> variables,
        Instant createdAt,
        Instant sentAt
) {
    public static EmailOutboxResponse from(EmailOutbox e) {
        return new EmailOutboxResponse(
                e.getId(),
                e.getRecipientEmail(),
                e.getSubject(),
                e.getTemplate(),
                e.getStatus().name(),
                e.getAttempts(),
                e.getMaxAttempts(),
                e.getLastError(),
                e.getVariables(),
                e.getCreatedAt(),
                e.getSentAt()
        );
    }
}
