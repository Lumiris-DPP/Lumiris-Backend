package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.entity.SellerAccount;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.SellerAccountRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// Même mécanique que SellerPayoutService (artisan) : le devis est encaissé sur le compte
// plateforme, la plateforme retient sa commission et reverse le NET au compte Connect du
// retoucheur — même SellerAccount, même modèle "separate charges & transfers".
@Service
@RequiredArgsConstructor
public class RepairPayoutService {

    private static final Logger log = LoggerFactory.getLogger(RepairPayoutService.class);

    private final StripeProperties properties;
    private final MarketplaceProperties marketplaceProperties;
    private final RepairRequestRepository requestRepository;
    private final SellerAccountRepository sellerAccountRepository;

    // Appelé à la clôture (RepairRequestService.complete) : calcule le net et déclenche le
    // virement. Idempotent (jamais deux transferts) ; ne fait rien si le compte du retoucheur
    // n'est pas encore activé — un balayage horaire pourrait rejouer plus tard si besoin.
    @Transactional
    public void releaseFunds(RepairRequest request) {
        if (!properties.hasSecretKey() || request.getStripeTransferId() != null) {
            return;
        }
        Long grossCents = request.getQuoteAmountCents();
        if (request.getPaidAt() == null || grossCents == null || grossCents <= 0) {
            return;
        }
        int netCents = (int) Math.round(grossCents * (1 - marketplaceProperties.getCommissionRate()));
        request.setNetCents(netCents);

        SellerAccount account = sellerAccountRepository
                .findByUser_Id(request.getRepairerProfile().getUser().getId())
                .filter(SellerAccount::isChargesEnabled)
                .orElse(null);
        if (account == null) {
            requestRepository.save(request);
            log.info("Réparation {} : compte Connect du retoucheur non activé — versement différé.", request.getId());
            return;
        }

        try {
            String chargeId = resolveChargeId(request.getStripePaymentIntentId());
            Transfer transfer = Transfer.create(
                    TransferCreateParams.builder()
                            .setAmount((long) netCents)
                            .setCurrency("eur")
                            .setDestination(account.getStripeAccountId())
                            .setSourceTransaction(chargeId)
                            .putMetadata("repair_request_id", request.getId().toString())
                            .build(),
                    RequestOptions.builder().setIdempotencyKey("repair-transfer:" + request.getId()).build());

            request.setStripeTransferId(transfer.getId());
            request.setReleasedAt(Instant.now());
            requestRepository.save(request);
            log.info("Réparation {} reversée → transfer {} ({}c) vers {}",
                    request.getId(), transfer.getId(), netCents, account.getStripeAccountId());
        } catch (StripeException e) {
            requestRepository.save(request);
            log.warn("Virement au retoucheur impossible pour {}: {}", request.getId(), e.getMessage());
        }
    }

    private String resolveChargeId(String paymentIntentId) throws StripeException {
        if (paymentIntentId == null) {
            throw new BillingValidationException("Aucun paiement rattaché à cette demande.");
        }
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        String chargeId = intent.getLatestCharge();
        if (chargeId == null) {
            throw new BillingValidationException("Aucun paiement capturé à reverser pour cette demande.");
        }
        return chargeId;
    }
}
