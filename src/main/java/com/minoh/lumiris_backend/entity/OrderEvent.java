package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Trace immuable d'une transition de commande (append-only, garanti par trigger côté base).
// Sert deux usages d'un seul enregistrement : la timeline de suivi affichée à l'acheteur et
// la piste d'audit exigée sur les litiges. Aucun setter : un événement ne se corrige pas.
@Entity
@Table(name = "marketplace_order_events")
@Getter
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private MarketplaceOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    private OrderActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(length = 2000)
    private String message;

    // Preuves jointes (photos d'un article abîmé, étiquette de retour, capture d'un suivi).
    // Chargées avec l'évènement : une timeline sans ses pièces jointes n'aurait aucun intérêt.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "marketplace_order_event_files",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "file_id"))
    private List<StoredFile> attachments = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderEvent() {}

    public OrderEvent(MarketplaceOrder order, OrderEventType type, OrderActorType actorType,
                      User actor, String message, List<StoredFile> attachments) {
        this.order = order;
        this.type = type;
        this.actorType = actorType;
        this.actor = actor;
        this.message = message;
        this.attachments = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);
        this.createdAt = Instant.now();
    }
}
