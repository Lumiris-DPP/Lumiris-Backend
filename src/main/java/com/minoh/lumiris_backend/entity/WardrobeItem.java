package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
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

    @Column(name = "warranty_description")
    private String warrantyDescription;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt = Instant.now();
}
