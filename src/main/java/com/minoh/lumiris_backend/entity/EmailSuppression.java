package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Adresses à ne plus jamais contacter (désinscription, bounce dur, plainte). Consultée avant
// tout envoi de prospection.
@Entity
@Table(name = "email_suppression")
@Getter
@Setter
public class EmailSuppression {

    public enum Reason { UNSUBSCRIBE, BOUNCE, COMPLAINT, MANUAL }

    @Id
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EmailSuppression() {}

    public EmailSuppression(String email, Reason reason) {
        this.email = email;
        this.reason = reason;
    }
}
