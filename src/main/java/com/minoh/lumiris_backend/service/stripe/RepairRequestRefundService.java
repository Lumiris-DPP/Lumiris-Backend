package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Remboursement du paiement d'un devis de retouche. Ne dépend que de Stripe — pas de
 * {@code RepairRequestService}, pour ne pas créer de cycle avec le service de paiement.
 * Le paiement est encaissé sur le compte plateforme (pas d'escrow / transfer pour l'instant),
 * donc un simple Refund suffit.
 */
@Service
@RequiredArgsConstructor
public class RepairRequestRefundService {

    private static final Logger log = LoggerFactory.getLogger(RepairRequestRefundService.class);

    private final StripeProperties properties;

    public void refundQuotePayment(RepairRequest request) {
        String paymentIntentId = request.getStripePaymentIntentId();
        if (paymentIntentId == null || !properties.hasSecretKey()) {
            return;
        }
        Refund refund = StripeCalls.billed("Remboursement du devis impossible", () ->
                Refund.create(RefundCreateParams.builder()
                        .setPaymentIntent(paymentIntentId)
                        .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                        .build()));
        log.info("Devis {} : remboursement {} émis", request.getId(), refund.getId());
    }
}
