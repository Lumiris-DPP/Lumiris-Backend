package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.exception.WebhookSignatureException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final StripeProperties properties;
    private final SubscriptionService subscriptionService;
    private final DirectSaleService directSaleService;
    private final SellerConnectService sellerConnectService;

    public void handle(String payload, String signatureHeader) {
        if (!properties.hasWebhookSecret()) {
            // Fail-closed : sans secret on ne PEUT PAS vérifier la signature. On refuse (500) plutôt que
            // d'acquitter (200) en silence — sinon, en prod mal configurée, des commandes payées ne
            // seraient jamais confirmées ni les vendeurs reversés, sans aucun signal. Le 500 fait
            // réessayer Stripe et remonte l'incident.
            log.error("Stripe webhook reçu mais STRIPE_WEBHOOK_SECRET n'est pas configuré — refus (fail-closed).");
            throw new IllegalStateException("Webhook Stripe non configuré (STRIPE_WEBHOOK_SECRET manquant).");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, properties.webhookSecret());
        } catch (SignatureVerificationException e) {
            throw new WebhookSignatureException("Signature de webhook invalide.");
        }

        switch (event.getType()) {
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> {
                String subscriptionId = subscriptionIdOf(event);
                if (subscriptionId != null) {
                    subscriptionService.resyncById(subscriptionId);
                    log.info("Resynced subscription {} ({})", subscriptionId, event.getType());
                } else {
                    log.warn("Subscription event {} ({}) carried no resolvable subscription id.",
                            event.getId(), event.getType());
                }
            }
            case "invoice.paid", "invoice.payment_succeeded", "invoice.payment_failed" -> {
                String subscriptionId = invoiceSubscriptionIdOf(event);
                if (subscriptionId != null) {
                    subscriptionService.resyncById(subscriptionId);
                }
            }
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            // LUMIRIS-22/24 : achat direct in-app payé (Payment Element) → fulfillment (Garde-Robe +
            // facture) et entrée dans le cycle de vie. Les fonds restent RETENUS : ils ne sont reversés
            // à l'atelier qu'à la livraison, pour qu'un remboursement reste possible entre-temps.
            case "payment_intent.succeeded" -> {
                StripeObject object = deserialize(event);
                if (object instanceof PaymentIntent pi && pi.getMetadata() != null
                        && "marketplace".equals(pi.getMetadata().get("order_type"))) {
                    directSaleService.fulfillByPaymentIntent(pi.getId());
                }
            }
            // LUMIRIS-22 : état d'un compte vendeur Connect (charges/payouts activés après onboarding).
            case "account.updated" -> {
                StripeObject object = deserialize(event);
                if (object instanceof Account account) {
                    sellerConnectService.syncFromStripe(account.getId());
                }
            }
            default -> log.debug("Unhandled Stripe event: {}", event.getType());
        }
    }

    private String subscriptionIdOf(Event event) {
        StripeObject object = deserialize(event);
        return object instanceof Subscription sub ? sub.getId() : null;
    }

    private String invoiceSubscriptionIdOf(Event event) {
        StripeObject object = deserialize(event);
        return object instanceof Invoice invoice ? invoice.getSubscription() : null;
    }

    private void handleCheckoutCompleted(Event event) {
        StripeObject object = deserialize(event);
        if (object instanceof Session session && session.getSubscription() != null) {
            subscriptionService.resyncById(session.getSubscription());
            log.info("Resynced subscription {} (checkout.session.completed)", session.getSubscription());
        }
    }

    private StripeObject deserialize(Event event) {
        Optional<StripeObject> typed = event.getDataObjectDeserializer().getObject();
        if (typed.isPresent()) {
            return typed.get();
        }
        try {
            return event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            log.warn("Could not deserialise event {} ({}): {}", event.getId(), event.getType(), e.getMessage());
            return null;
        }
    }
}
