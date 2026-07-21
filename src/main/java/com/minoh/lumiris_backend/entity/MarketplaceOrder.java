package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// Commande d'achat direct in-app. commissionCents = part plateforme (application_fee) prélevée
// à la source — traçée ici, jamais dans le tri/suggestions ni liée au score Iris.
@Entity
@Table(name = "marketplace_orders")
@Getter
@Setter
public class MarketplaceOrder extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private MarketplaceProduct product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id")
    private DppForm dppForm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_user_id")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_user_id")
    private User seller;

    @Column(name = "stripe_checkout_session_id", unique = true)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "amount_total_cents", nullable = false)
    private int amountTotalCents = 0;

    // Frais de port du panier (portés par la 1re ligne, 0 sur les autres) — permet de reconstituer
    // le montant réellement débité (articles + livraison) sur l'écran de confirmation.
    @Column(name = "shipping_cents", nullable = false)
    private int shippingCents = 0;

    @Column(name = "commission_cents", nullable = false)
    private int commissionCents = 0;

    @Column(nullable = false)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    // ── Escrow : fonds encaissés sur la plateforme puis reversés au vendeur par un Transfer Stripe ──
    // netCents = part reversée au vendeur (brut de la ligne − commission plateforme).
    @Column(name = "net_cents", nullable = false)
    private int netCents = 0;

    // Id du Transfer Stripe une fois les fonds libérés vers le compte connecté (null = encore retenu).
    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    // Relie la charge et son (ses) transfert(s) côté Stripe (reporting / réconciliation).
    @Column(name = "transfer_group")
    private String transferGroup;

    // Date de libération des fonds au vendeur (Transfer créé).
    @Column(name = "released_at")
    private Instant releasedAt;
}
