package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Un e-mail de prospection envoyé pour inviter un retoucheur à réclamer sa fiche annuaire.
@Entity
@Table(name = "repairer_prospect_outreach")
@Getter
@Setter
public class RepairerProspectOutreach {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repairer_profile_id", nullable = false)
    private RepairerProfile repairerProfile;

    @Column(nullable = false)
    private String email;

    // Jeton de réclamation porté par l'e-mail — permet de tracer le clic et de relier la
    // réclamation à cet envoi.
    @Column
    private UUID token;

    @Column(name = "contact_count", nullable = false)
    private int contactCount = 1;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "clicked_at")
    private Instant clickedAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
