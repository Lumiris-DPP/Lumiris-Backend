package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.exception.WebhookSignatureException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
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

    public void handle(String payload, String signatureHeader) {
        if (!properties.hasWebhookSecret()) {
            log.error("Stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured — event ignored.");
            return;
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
