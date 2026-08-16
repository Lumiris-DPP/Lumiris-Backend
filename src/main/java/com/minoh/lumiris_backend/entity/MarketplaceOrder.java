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
    @JoinColumn(name = "variant_id")
    private MarketplaceProductVariant variant;

    // Libellé de la déclinaison GELÉ à la commande : un atelier retire légitimement une taille de
    // son catalogue, et sans lui la facture comme un litige « mauvaise taille » perdent leur objet.
    @Column(name = "variant_label", length = 120)
    private String variantLabel;

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

    @Column(nullable = false)
    private int quantity = 1;

    // Frais de port du panier (portés par la 1re ligne de chaque atelier, 0 sur les autres) —
    // permet de reconstituer le montant réellement débité (articles + livraison).
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

    // ── Adresse de livraison (saisie au checkout, indispensable au vendeur pour expédier) ──
    @Column(name = "ship_to_name", length = 200)
    private String shipToName;

    @Column(name = "ship_to_line1", length = 300)
    private String shipToLine1;

    @Column(name = "ship_to_line2", length = 300)
    private String shipToLine2;

    @Column(name = "ship_to_postal_code", length = 20)
    private String shipToPostalCode;

    @Column(name = "ship_to_city", length = 120)
    private String shipToCity;

    @Column(name = "ship_to_country", length = 2)
    private String shipToCountry = "FR";

    @Column(name = "ship_to_phone", length = 40)
    private String shipToPhone;

    // ── Expédition ──────────────────────────────────────────────────────────
    @Column(length = 80)
    private String carrier;

    @Column(name = "tracking_number", length = 120)
    private String trackingNumber;

    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    // Date d'expédition promise à l'acheteur, figée à l'encaissement (délai de préparation de
    // l'annonce + congés éventuels de l'atelier). Sert d'origine à la relance vendeur et à
    // l'échéancier de versement.
    @Column(name = "ship_due_at")
    private Instant shipDueAt;

    @Column(name = "ship_reminder_sent_at")
    private Instant shipReminderSentAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // ── Retour ──────────────────────────────────────────────────────────────
    // Échéance au-delà de laquelle plus aucun retour n'est recevable (livraison + fenêtre légale).
    @Column(name = "return_deadline")
    private Instant returnDeadline;

    @Column(name = "return_requested_at")
    private Instant returnRequestedAt;

    @Column(name = "return_reason", length = 2000)
    private String returnReason;

    @Column(name = "return_decided_at")
    private Instant returnDecidedAt;

    @Column(name = "return_decision_note", length = 2000)
    private String returnDecisionNote;

    @Column(name = "return_received_at")
    private Instant returnReceivedAt;

    // ── Remboursement ───────────────────────────────────────────────────────
    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    // Montant réellement remboursé à l'acheteur (peut être partiel : frais de retour retenus, …).
    @Column(name = "refunded_cents", nullable = false)
    private int refundedCents = 0;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_reason", length = 2000)
    private String refundReason;

    // Reprise des fonds déjà reversés au vendeur, quand le remboursement suit la libération.
    @Column(name = "stripe_transfer_reversal_id")
    private String stripeTransferReversalId;

    // ── Litige ──────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false, length = 16)
    private DisputeStatus disputeStatus = DisputeStatus.NONE;

    @Column(name = "dispute_opened_at")
    private Instant disputeOpenedAt;

    @Column(name = "dispute_reason", length = 2000)
    private String disputeReason;

    @Column(name = "dispute_resolution", length = 2000)
    private String disputeResolution;

    @Column(name = "dispute_closed_at")
    private Instant disputeClosedAt;
}
