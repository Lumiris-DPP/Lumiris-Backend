package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Pièce possédée par l'acheteur (Garde-Robe VISION). Créée automatiquement à l'achat direct :
// rattachée au passeport (dpp_form) et à la commande, avec sa facture et sa garantie.
@Entity
@Table(name = "wardrobe_items")
@Getter
@Setter
public class WardrobeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id")
    private DppForm dppForm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private MarketplaceOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WardrobeItemOrigin origin = WardrobeItemOrigin.PURCHASE;

    @Column(name = "client_key", length = 255)
    private String clientKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private WardrobeItemKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "warranty_description")
    private String warrantyDescription;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt = Instant.now();

    // Échéance FIGÉE à l'achat : un atelier peut raccourcir la garantie de ses futures pièces, pas
    // celle déjà vendue. Null = aucune durée déclarée sur le passeport, donc aucune alerte.
    @Column(name = "warranty_until")
    private Instant warrantyUntil;

    // Anti-doublons du balayage (quotidien, sur chaque instance) — pas des dates métier.
    // La saison, et non un simple drapeau : le rappel d'entretien doit revenir l'hiver suivant.
    @Column(name = "care_reminder_sent_at")
    private Instant careReminderSentAt;

    @Column(name = "care_reminder_season", length = 16)
    private String careReminderSeason;

    @Column(name = "warranty_alert_sent_at")
    private Instant warrantyAlertSentAt;
}
