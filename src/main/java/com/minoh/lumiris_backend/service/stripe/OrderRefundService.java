package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.exception.BillingValidationException;
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

// LUMIRIS-24 · Remboursement Stripe d'une ligne de commande, partiel ou total. Les appels Stripe
// vivent ici seuls : le service ne touche jamais au statut de la commande (c'est le rôle de
// OrderLifecycleService), il renvoie les identifiants à persister.
//
// Modèle escrow (separate charges & transfers) : rembourser l'acheteur ne reprend PAS
// automatiquement les fonds déjà reversés au vendeur. Quand la libération a déjà eu lieu, on
// crée d'abord un TransferReversal (reprise sur le compte connecté) puis le Refund — sinon la
// plateforme rembourserait de sa poche.
@Service
@RequiredArgsConstructor
public class OrderRefundService {

    private static final Logger log = LoggerFactory.getLogger(OrderRefundService.class);

    private final StripeProperties properties;

    public record RefundOutcome(String refundId, String transferReversalId, int amountCents) {}

    public RefundOutcome refund(MarketplaceOrder order, int amountCents, String reason) {
        properties.requireSecretKey();
        if (order.getStripePaymentIntentId() == null) {
            throw new BillingValidationException("Aucun paiement rattaché à cette commande.");
        }
        int refundable = refundableCents(order);
        if (amountCents <= 0 || amountCents > refundable) {
            throw new BillingValidationException(
                    "Montant de remboursement invalide (maximum remboursable : " + refundable + " centimes).");
        }

        // Reprise proportionnelle chez le vendeur : il ne doit ni supporter la commission d'une
        // vente annulée, ni conserver le net d'un article remboursé.
        String reversalId = order.getStripeTransferId() != null
                ? reverseTransfer(order, reversalAmount(order, amountCents))
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
                                .setIdempotencyKey("refund:" + order.getId() + ":" + amountCents)
                                .build()));

        log.info("Commande {} remboursée : {}c (refund {}, reversal {})",
                order.getId(), amountCents, refund.getId(), reversalId);
        return new RefundOutcome(refund.getId(), reversalId, amountCents);
    }

    // Montant encore remboursable sur la ligne : le débit de l'acheteur (articles + port) moins
    // ce qui lui a déjà été rendu.
    public int refundableCents(MarketplaceOrder order) {
        return Math.max(0, order.getAmountTotalCents() + order.getShippingCents() - order.getRefundedCents());
    }

    // Part du remboursement à reprendre au vendeur : son net rapporté au débit de l'acheteur.
    // Le reste (la commission au prorata) est renoncé par la plateforme.
    private long reversalAmount(MarketplaceOrder order, int refundCents) {
        int charged = order.getAmountTotalCents() + order.getShippingCents();
        if (charged <= 0) {
            return 0;
        }
        long amount = Math.round((double) order.getNetCents() * refundCents / charged);
        return Math.min(amount, order.getNetCents());
    }

    private String reverseTransfer(MarketplaceOrder order, long amount) {
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
                            .setIdempotencyKey("reversal:" + order.getId() + ":" + amount)
                            .build());
        });
        return reversal.getId();
    }
}
