package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// Trace immuable d'une action admin sensible (import, invitation, validation…).
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public AdminAuditLog() {}

    public AdminAuditLog(String actorEmail, String action, String targetType, String targetId, String detail) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

    public UUID getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
