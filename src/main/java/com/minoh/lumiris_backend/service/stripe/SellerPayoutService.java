package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.SellerAccount;
import com.minoh.lumiris_backend.exception.BillingException;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.repository.SellerAccountRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Verse à l'atelier les fonds d'une commande éligible.
@Service
@RequiredArgsConstructor
public class SellerPayoutService {

    private static final Logger log = LoggerFactory.getLogger(SellerPayoutService.class);
    private static final Set<OrderStatus> RELEASABLE = Set.of(OrderStatus.DELIVERED, OrderStatus.COMPLETED);

    private final StripeProperties properties;
    private final SellerAccountRepository sellerAccountRepository;

    // Verse les fonds seulement si la commande est éligible.
    @Transactional(propagation = Propagation.MANDATORY,
            noRollbackFor = {BillingException.class, BillingValidationException.class})
    public Optional<String> createTransfer(MarketplaceOrder order) {
        if (!properties.hasSecretKey() || order.getStripeTransferId() != null) {
            return Optional.empty();
        }
        if (!RELEASABLE.contains(order.getStatus()) || order.getNetCents() <= 0) {
            return Optional.empty();
        }
        SellerAccount account = sellerAccountRepository.findByUser_Id(order.getSeller().getId())
                .filter(SellerAccount::isChargesEnabled)
                .orElseThrow(() -> new BillingValidationException(
                        "Compte de paiement du vendeur non activé — impossible de reverser."));
        try {
            String chargeId = resolveChargeId(order.getStripePaymentIntentId());
            Transfer transfer = Transfer.create(
                    TransferCreateParams.builder()
                            .setAmount((long) order.getNetCents())
                            .setCurrency(order.getCurrency().toLowerCase())
                            .setDestination(account.getStripeAccountId())
                            .setSourceTransaction(chargeId)
                            .setTransferGroup(order.getTransferGroup())
                            .putMetadata("order_id", order.getId().toString())
                            .build(),
                    RequestOptions.builder().setIdempotencyKey("transfer:" + order.getId()).build());

            log.info("Commande {} reversée → transfer {} ({}c) vers {}",
                    order.getId(), transfer.getId(), order.getNetCents(), account.getStripeAccountId());
            return Optional.of(transfer.getId());
        } catch (StripeException e) {
            throw new BillingException("Virement au vendeur impossible: " + e.getMessage(), e);
        }
    }

    // Retrouve l'encaissement Stripe associé au paiement de la commande.
    private String resolveChargeId(String paymentIntentId) throws StripeException {
        if (paymentIntentId == null) {
            throw new BillingValidationException("Aucun paiement rattaché à cette vente.");
        }
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        String chargeId = intent.getLatestCharge();
        if (chargeId == null) {
            throw new BillingValidationException("Aucun paiement capturé à reverser pour cette vente.");
        }
        return chargeId;
    }
}
