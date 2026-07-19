package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// Une annonce du catalogue artisan. Le score comparable (tri "score équivalent ou
// supérieur") provient exclusivement du DPP lié (dpp_iris_scores.total) : jamais d'un
// champ dénormalisé, jamais de la commission.
@Entity
@Table(name = "marketplace_products")
@Getter
@Setter
public class MarketplaceProduct extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artisan_profile_id", nullable = false)
    private ArtisanProfile artisanProfile;

    // Passeport source du produit — porteur du score Iris. Nullable : un brouillon
    // peut exister avant d'être rattaché à un DPP (il n'apparaît alors pas au tri par score).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id")
    private DppForm dppForm;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    private String material;

    @Column(name = "origin_country")
    private String originCountry;

    @Column(name = "price_cents", nullable = false)
    private int priceCents = 0;

    @Column(nullable = false)
    private String currency = "EUR";

    @Column(nullable = false)
    private int stock = 0;

    @Column(name = "external_order_url")
    private String externalOrderUrl;

    @Column(name = "photo_url")
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MarketplaceProductStatus status = MarketplaceProductStatus.DRAFT;

    @Column(name = "stripe_product_id")
    private String stripeProductId;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    // Offre de vente directe (LUMIRIS-22) : frais de port + conditions de retour de l'annonce.
    @Column(name = "shipping_cents", nullable = false)
    private int shippingCents = 0;

    @Column(name = "return_policy")
    private String returnPolicy;

    // Compteur de vues de la fiche produit (incrémenté à l'ouverture côté VISION) — statistiques vendeur.
    @Column(name = "views", nullable = false)
    private long views = 0;
}

