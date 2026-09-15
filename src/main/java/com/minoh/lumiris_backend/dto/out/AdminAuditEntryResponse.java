package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.AdminAuditLog;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditEntryResponse(
        UUID id,
        String actorEmail,
        String action,
        String targetType,
        String targetId,
        String detail,
        Instant occurredAt
) {
    public static AdminAuditEntryResponse from(AdminAuditLog log) {
        return new AdminAuditEntryResponse(
                log.getId(), log.getActorEmail(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getDetail(), log.getOccurredAt());
    }
}
