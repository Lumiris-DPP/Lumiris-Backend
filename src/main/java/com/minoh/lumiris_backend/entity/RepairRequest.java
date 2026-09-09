package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_requests")
@Getter
@Setter
public class RepairRequest extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repairer_profile_id", nullable = false)
    private RepairerProfile repairerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_user_id", nullable = false)
    private User consumerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id", nullable = false)
    private DppForm dppForm;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairRequestStatus status = RepairRequestStatus.PENDING;

    @Column(name = "quote_amount_cents")
    private Long quoteAmountCents;

    @Column(name = "quote_description")
    private String quoteDescription;

    @Column(name = "quote_submitted_at")
    private Instant quoteSubmittedAt;

    @Column(name = "quote_refused_at")
    private Instant quoteRefusedAt;

    @Column(name = "appointment_at")
    private Instant appointmentAt;

    // Paiement du devis : PaymentIntent encaissé sur le compte plateforme.
    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "paid_at")
    private Instant paidAt;
}
