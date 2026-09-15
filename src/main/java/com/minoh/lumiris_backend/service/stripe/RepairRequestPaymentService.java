package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.dto.out.RepairPaymentIntentResponse;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.service.RepairRequestService;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Paiement du devis de retouche par PaymentIntent (Payment Element embarqué côté VISION).
 * Encaissé sur le compte PLATEFORME — pas d'escrow / transfer pour l'instant : le reversement au
 * retoucheur se fera hors bande jusqu'à ce que les retoucheurs aient un compte Connect.
 * Le paiement vaut acceptation du devis : c'est le webhook {@code payment_intent.succeeded}
 * (order_type=repair) qui fait passer la demande en ACCEPTED.
 */
@Service
@RequiredArgsConstructor
public class RepairRequestPaymentService {

    private static final String CURRENCY = "eur";

    private final StripeProperties properties;
    private final RepairRequestService requestService;

    @Transactional
    public RepairPaymentIntentResponse createQuotePaymentIntent(
            String consumerEmail, UUID requestId, Instant appointmentAt) {
        properties.requireSecretKey();
        RepairRequest request = requestService.requirePayableQuote(consumerEmail, requestId);
        long amount = request.getQuoteAmountCents();

        PaymentIntent intent = StripeCalls.billed("Préparation du paiement impossible", () ->
                PaymentIntent.create(
                        PaymentIntentCreateParams.builder()
                                .setAmount(amount)
                                .setCurrency(CURRENCY)
                                .setReceiptEmail(request.getConsumerUser().getEmail())
                                .setAutomaticPaymentMethods(
                                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                                .setEnabled(true).build())
                                .putMetadata("order_type", "repair")
                                .putMetadata("repair_request_id", requestId.toString())
                                .build(),
                        RequestOptions.builder()
                                .setIdempotencyKey("repair-quote:" + requestId + ":" + amount)
                                .build()));

        requestService.attachPaymentIntent(requestId, intent.getId(), appointmentAt);

        return new RepairPaymentIntentResponse(
                intent.getClientSecret(), properties.publishableKey(), amount, CURRENCY);
    }
}
