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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Rembourse un paiement et reprend le versement correspondant.
@Service
@RequiredArgsConstructor
public class OrderRefundService {

    private static final Logger log = LoggerFactory.getLogger(OrderRefundService.class);

    private final StripeProperties properties;

    // Regroupe le montant et les références d'un remboursement.
    public record RefundOutcome(String refundId, String transferReversalId, int amountCents) {}

    // Rembourse le montant demandé avec une référence d'opération stable.
    @Transactional(propagation = Propagation.MANDATORY)
    public RefundOutcome refund(MarketplaceOrder order, int amountCents, String reason) {
        return refund(order, amountCents, reason, order.getRefundedCents() + ":" + amountCents);
    }

    // Rembourse le montant demandé avec une référence d'opération stable.
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
        String fullReason = reason == null ? "" : reason;
        boolean hashedReason = fullReason.codePointCount(0, fullReason.length()) > 500;
        String stripeReason = hashedReason ? "sha256:" + reasonHash(reason) : fullReason;
        String reversalId = order.getStripeTransferId() != null
                ? reverseTransfer(order, amountCents, reason, operationKey)
                : null;

        Refund refund = StripeCalls.billed("Remboursement impossible", () ->
                Refund.create(
                        RefundCreateParams.builder()
                                .setPaymentIntent(order.getStripePaymentIntentId())
                                .setAmount((long) amountCents)
                                .putMetadata("order_id", order.getId().toString())
                                .putMetadata("reason", stripeReason)
                                .build(),
                        RequestOptions.builder()
                                .setIdempotencyKey("refund:" + order.getId() + ":" + operationKey
                                        + (hashedReason ? ":reason-sha256" : ""))
                                .build()));

        log.info("Commande {} remboursée : {}c (refund {}, reversal {})",
                order.getId(), amountCents, refund.getId(), reversalId);
        return new RefundOutcome(refund.getId(), reversalId, amountCents);
    }

    // Calcule le montant encore remboursable de la commande.
    public int refundableCents(MarketplaceOrder order) {
        return Math.max(0, order.getAmountTotalCents() + order.getShippingCents() - order.getRefundedCents());
    }

    // Reprend le reliquat cumulé ou retrouve la reprise de cette opération.
    private String reverseTransfer(MarketplaceOrder order, int refundCents, String reason, String operationKey) {
        return StripeCalls.billed("Reprise des fonds au vendeur impossible", () -> {
            Transfer transfer = Transfer.retrieve(order.getStripeTransferId());
            if (!Objects.equals(transfer.getAmount(), (long) order.getNetCents())
                    || transfer.getAmountReversed() == null || transfer.getAmountReversed() < 0
                    || transfer.getAmountReversed() > transfer.getAmount()) {
                throw new BillingValidationException("Versement Stripe incohérent : rapprochement nécessaire.");
            }
            String reasonHash = reasonHash(reason);
            String existingId = null;
            long reversed = 0;
            var page = transfer.getReversals();
            var history = new ArrayList<>(page.getData());
            while (Boolean.TRUE.equals(page.getHasMore())) {
                if (page.getData().isEmpty()) {
                    throw new BillingValidationException("Historique de reprise incomplet : rapprochement nécessaire.");
                }
                page = page.list(Map.of("starting_after", page.getData().getLast().getId()));
                history.addAll(page.getData());
            }
            for (TransferReversal previous : history) {
                reversed += previous.getAmount();
                Map<String, String> metadata = previous.getMetadata();
                String previousKey = metadata.get("operation_key");
                if (previousKey == null) {
                    if (!previous.getId().equals(order.getStripeTransferReversalId())) {
                        throw new BillingValidationException("Ancienne reprise non rattachée : rapprochement nécessaire.");
                    }
                    continue;
                }
                int before;
                try {
                    before = Integer.parseInt(metadata.get("refunded_before"));
                } catch (NumberFormatException e) {
                    throw new BillingValidationException("Historique de reprise invalide : rapprochement nécessaire.");
                }
                if (previousKey.equals(operationKey)) {
                    if (existingId != null || before != order.getRefundedCents()
                            || !Integer.toString(refundCents).equals(metadata.get("refund_cents"))
                            || !reasonHash.equals(metadata.get("reason_hash"))) {
                        throw new BillingValidationException("Cette reprise désigne une autre opération de remboursement.");
                    }
                    existingId = previous.getId();
                } else if (before >= order.getRefundedCents()) {
                    throw new BillingValidationException("Une reprise attend son remboursement : rejouez cette opération.");
                }
            }
            if (reversed != transfer.getAmountReversed()) {
                throw new BillingValidationException("Historique de reprise incomplet : rapprochement nécessaire.");
            }
            if (existingId != null) return existingId;
            long charged = (long) order.getAmountTotalCents() + order.getShippingCents();
            long cumulative = (long) order.getRefundedCents() + refundCents;
            long target = (order.getNetCents() * cumulative + charged / 2) / charged;
            long amount = Math.max(0, target - reversed);
            if (amount == 0) return null;
            return transfer.getReversals().create(
                    TransferReversalCollectionCreateParams.builder()
                            .setAmount(amount)
                            .putMetadata("order_id", order.getId().toString())
                            .putMetadata("operation_key", operationKey)
                            .putMetadata("refunded_before", Integer.toString(order.getRefundedCents()))
                            .putMetadata("refund_cents", Integer.toString(refundCents))
                            .putMetadata("reason_hash", reasonHash)
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("reversal:" + order.getId() + ":" + operationKey)
                            .build()).getId();
        });
    }

    // Calcule une empreinte du motif de remboursement.
    private String reasonHash(String reason) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((reason == null ? "" : reason).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
