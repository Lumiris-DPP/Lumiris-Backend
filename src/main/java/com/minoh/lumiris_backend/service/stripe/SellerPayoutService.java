package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.SellerAccount;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.BillingException;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.SellerAccountRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
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
import java.util.UUID;

// LUMIRIS · Escrow marketplace — reversement des fonds au vendeur. Le paiement est encaissé sur le compte
// PLATEFORME (separate charges & transfers, cf. DirectSaleService) ; la plateforme conserve la commission
// et reverse le NET au compte connecté du vendeur via un Transfer (source_transaction = charge, pour tirer
// sur cette vente précise). Déclenché AUTOMATIQUEMENT dès l'encaissement confirmé (webhook), avec un retry
// manuel possible si le reversement automatique a échoué. Idempotent : jamais deux transferts par commande.
@Service
@RequiredArgsConstructor
public class SellerPayoutService {

    private static final Logger log = LoggerFactory.getLogger(SellerPayoutService.class);

    private final StripeProperties properties;
    private final MarketplaceOrderRepository orderRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final UserRepository userRepository;

    // Reversement automatique après encaissement (appelé par le webhook payment_intent.succeeded).
    // Best-effort par ligne : un échec laisse les fonds retenus (retry via releaseOrder) sans bloquer les autres.
    @Transactional
    public void autoReleaseByPaymentIntent(String paymentIntentId) {
        if (!properties.hasSecretKey()) {
            return;
        }
        for (MarketplaceOrder order : orderRepository.findByStripePaymentIntentId(paymentIntentId)) {
            try {
                transferToSeller(order, false);
            } catch (RuntimeException e) {
                log.warn("Reversement auto impossible pour la commande {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    // Retry manuel du reversement par le vendeur (si le reversement auto a échoué). Authentifié.
    @Transactional
    public void releaseOrder(String sellerEmail, UUID orderId) {
        properties.requireSecretKey();
        User seller = requireArtisan(sellerEmail);
        MarketplaceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Vente introuvable."));
        if (order.getSeller() == null || !order.getSeller().getId().equals(seller.getId())) {
            throw new RoleNotAllowedException("Cette vente n'appartient pas à votre atelier.");
        }
        transferToSeller(order, true);
    }

    // Crée le Transfer vers le compte connecté du vendeur et persiste l'état. No-op si déjà reversé.
    // strict=true (retry manuel) remonte les erreurs ; strict=false (auto) les laisse propager au appelant
    // qui les catch par ligne.
    private void transferToSeller(MarketplaceOrder order, boolean strict) {
        if (order.getStripeTransferId() != null) {
            return; // déjà reversé — idempotent.
        }
        if (order.getStatus() != OrderStatus.PAID) {
            if (strict) {
                throw new BillingValidationException(
                        "Seule une vente payée peut être reversée (statut: " + order.getStatus() + ").");
            }
            return;
        }
        if (order.getNetCents() <= 0) {
            if (strict) {
                throw new BillingValidationException("Aucun montant à reverser pour cette vente.");
            }
            return;
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

            order.setStripeTransferId(transfer.getId());
            order.setReleasedAt(Instant.now());
            orderRepository.save(order);
            log.info("Commande {} reversée → transfer {} ({}c) vers {}",
                    order.getId(), transfer.getId(), order.getNetCents(), account.getStripeAccountId());
        } catch (StripeException e) {
            throw new BillingException("Virement au vendeur impossible: " + e.getMessage(), e);
        }
    }

    // Récupère la charge sous-jacente du PaymentIntent, cible du transfert (source_transaction).
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

    private User requireArtisan(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ARTISAN) {
            throw new RoleNotAllowedException("Seuls les artisans peuvent reverser une vente.");
        }
        return user;
    }
}
