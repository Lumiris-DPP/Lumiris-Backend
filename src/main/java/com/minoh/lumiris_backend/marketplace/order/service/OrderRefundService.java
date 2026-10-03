package com.minoh.lumiris_backend.marketplace.order.service;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.integration.stripe.StripeCalls;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversal;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferReversalCollectionCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Exécute les remboursements Stripe et les reprises de fonds par opération. */
@Service
@RequiredArgsConstructor
public class OrderRefundService {

    private static final Logger log = LoggerFactory.getLogger(OrderRefundService.class);

    private final StripeProperties properties;

    /** Décrit les données de RefundOutcome pour les commandes. */
    public record RefundOutcome(String refundId, String transferReversalId, int amountCents) {}

    /** Exécute une opération de remboursement avec une clé stable pour ses reprises. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundOutcome refund(MarketplaceOrder order, int amountCents, String reason) {
        return refund(order, amountCents, reason, order.getRefundedCents() + ":" + amountCents);
    }

    /** Exécute une opération de remboursement avec une clé stable pour ses reprises. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundOutcome refund(MarketplaceOrder order, int amountCents, String reason, String operationKey) {
        properties.requireSecretKey();
        if (order.getStripePaymentIntentId() == null) {
            throw new BillingValidationException("Aucun paiement rattaché à cette commande.");
        }
        int refundable = refundableCents(order);
        if (amountCents <= 0 || amountCents > refundable) {
            throw new BillingValidationException(
                    "Montant de remboursement invalide (maximum remboursable : " + refundable + " centimes).");
        }
        String reversalId = order.getStripeTransferId() != null
                ? reverseTransfer(order, reversalAmount(order, amountCents), operationKey)
                : null;

        Refund refund = StripeCalls.billed("Remboursement impossible", () ->
                Refund.create(
                        RefundCreateParams.builder()
                                .setPaymentIntent(order.getStripePaymentIntentId())
                                .setAmount((long) amountCents)
                                .putMetadata("order_id", order.getId().toString())
                                .putMetadata("reason", reason == null ? "" : reason)
                                .build(),
                        RequestOptions.builder()
                                .setIdempotencyKey("refund:" + order.getId() + ":" + operationKey)
                                .build()));

        log.info("Commande {} remboursée : {}c (refund {}, reversal {})",
                order.getId(), amountCents, refund.getId(), reversalId);
        return new RefundOutcome(refund.getId(), reversalId, amountCents);
    }

    /** Calcule le solde encore remboursable de la commande. */
    public int refundableCents(MarketplaceOrder order) {
        return Math.max(0, order.getAmountTotalCents() + order.getShippingCents() - order.getRefundedCents());
    }

    /** Calcule la part proportionnelle du vendeur selon la règle existante. */
    private long reversalAmount(MarketplaceOrder order, int refundCents) {
        int charged = order.getAmountTotalCents() + order.getShippingCents();
        if (charged <= 0) {
            return 0;
        }
        long amount = Math.round((double) order.getNetCents() * refundCents / charged);
        return Math.min(amount, order.getNetCents());
    }

    /** Reprend les fonds du vendeur sous la clé stable de l’opération. */
    private String reverseTransfer(MarketplaceOrder order, long amount, String operationKey) {
        if (amount <= 0) {
            return null;
        }
        TransferReversal reversal = StripeCalls.billed("Reprise des fonds au vendeur impossible", () -> {
            Transfer transfer = Transfer.retrieve(order.getStripeTransferId());
            return transfer.getReversals().create(
                    TransferReversalCollectionCreateParams.builder()
                            .setAmount(amount)
                            .putMetadata("order_id", order.getId().toString())
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("reversal:" + order.getId() + ":" + operationKey)
                            .build());
        });
        return reversal.getId();
    }
}
