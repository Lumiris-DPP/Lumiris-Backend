package com.minoh.lumiris_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Ligne de la file d'emails transactionnels. Écrite par MailService (dans la transaction de
// l'appelant), consommée par EmailOutboxDispatcher qui rend le template et appelle Resend.
@Entity
@Table(name = "email_outbox")
@Getter
@Setter
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    // Nom du template Thymeleaf (ex: "email/payment-success"), résolu par le dispatcher au moment
    // de l'envoi — pas de HTML pré-rendu stocké ici.
    @Column(nullable = false, length = 100)
    private String template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> variables;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;
}
