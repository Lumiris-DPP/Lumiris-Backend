package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Abonnement d'un acheteur à une pièce : la liste d'envies, et le destinataire des deux alertes
// déclenchées par le stock et le prix réels.
@Entity
@Table(name = "marketplace_favorites")
@Getter
@Setter
public class MarketplaceFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private MarketplaceProduct product;

    // Repère de prix propre à ce favori, jamais remonté par une hausse : c'est lui qui rend une
    // baisse déjà annoncée non réannonçable.
    @Column(name = "last_price_cents", nullable = false)
    private int lastPriceCents;

    @Column(name = "low_stock_notified_at")
    private Instant lowStockNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
