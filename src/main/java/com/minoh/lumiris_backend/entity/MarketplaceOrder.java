package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "commission_cents", nullable = false)
    private int commissionCents = 0;

    @Column(nullable = false)
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "invoice_number")
    private String invoiceNumber;
}
