package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.domain.BillingCycle;
import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.domain.StripeSubscriptionStatus;
import com.minoh.lumiris_backend.dto.out.QuotaResponse;
import com.minoh.lumiris_backend.dto.out.SubscriptionResponse;
import com.minoh.lumiris_backend.dto.out.SubscriptionStateResponse;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.exception.BillingException;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.service.QuotaService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.billingportal.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.billingportal.SessionCreateParams;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Payment Element flow: createSetupIntent → front confirms → confirmSubscription.
// Stripe network calls run outside any DB transaction; persistence is delegated to SubscriptionSyncService.
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    // SetupIntent status (distinct from a subscription status) signalling the payment method is confirmed.
    private static final String SETUP_INTENT_SUCCEEDED = "succeeded";

    private final StripeProperties properties;
    private final StripeCustomerService customerService;
    private final StripeCatalogService catalogService;
    private final SubscriptionSyncService syncService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final QuotaService quotaService;

    public record SetupIntentResult(
            String clientSecret,
            String publishableKey,
            PlanTier tier,
            BillingCycle cycle,
            String priceId,
            long amountCents
    ) {}

    // ATELIER+ est une OPTION (add-on) : on ne peut pas la souscrire sans un abonnement ATELIER de base,
    // ni comme plan autonome / de bascule. (La détention simultanée base + ATELIER+ relève de la
    // gestion d'add-ons Stripe — 2e item d'abonnement — pas encore branchée.)
    private void assertNotStandaloneAddon(PlanTier tier) {
        if (tier == PlanTier.ATELIER_PLUS) {
            throw new BillingValidationException(
                    "ATELIER+ est une option à ajouter à un abonnement ATELIER actif (Solo, Studio ou Maison), "
                            + "pas un abonnement autonome.");
        }
    }

    public SetupIntentResult createSetupIntent(String userEmail, PlanTier tier, BillingCycle cycle) {
        properties.requireSecretKey();
        assertNotStandaloneAddon(tier);
        User user = userRepository.getByEmail(userEmail);
        String customerId = customerService.ensureCustomer(user);
        String priceId = catalogService.priceId(tier, cycle);
        return StripeCalls.billed("Préparation du paiement impossible", () -> {
            // Card only: no wallets/SEPA/redirect methods — explicit payment_method_types
            // (mutually exclusive with automaticPaymentMethods).
            // Méthodes automatiques pour l'abonnement (SaaS) : carte + wallets Apple Pay / Google Pay
            // (wallets uniquement en HTTPS + domaine vérifié → invisibles en local http). Redirections
            // désactivées : le moyen est enregistré off-session pour la facturation récurrente sans
            // quitter la page (les méthodes à redirection type Klarna ne conviennent pas au récurrent).
            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .setAutomaticPaymentMethods(SetupIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .setAllowRedirects(SetupIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                            .build())
                    .putMetadata("user_id", user.getId().toString())
                    .putMetadata("tier", tier.key())
                    .putMetadata("cycle", cycle.key())
                    .putMetadata("price_id", priceId)
                    .build();
            SetupIntent intent = SetupIntent.create(params);
            return new SetupIntentResult(intent.getClientSecret(), properties.publishableKey(),
                    tier, cycle, priceId, tier.amountCents(cycle));
        });
    }

    public UserSubscription confirmSubscription(String userEmail, String setupIntentId) {
        properties.requireSecretKey();
        User user = userRepository.getByEmail(userEmail);
        subscriptionRepository.findByUserId(user.getId())
                .filter(s -> StripeSubscriptionStatus.isLive(s.getStatus()))
                .ifPresent(s -> {
                    throw new ConflictException(
                            "Vous avez déjà un abonnement en cours. Utilisez le portail pour le modifier.");
                });
        try {
            SetupIntent intent = SetupIntent.retrieve(setupIntentId);
            if (!SETUP_INTENT_SUCCEEDED.equals(intent.getStatus())) {
                throw new BillingValidationException("Le moyen de paiement n'a pas été confirmé (statut: "
                        + intent.getStatus() + ").");
            }
            String customerId = customerService.ensureCustomer(user);
            if (intent.getCustomer() != null && !intent.getCustomer().equals(customerId)) {
                throw new BillingValidationException("Le moyen de paiement n'appartient pas à ce compte.");
            }
            String paymentMethodId = intent.getPaymentMethod();
            if (paymentMethodId == null) {
                throw new BillingValidationException("Aucun moyen de paiement associé à la confirmation.");
            }

            setCustomerDefaultPaymentMethod(customerId, paymentMethodId);

            String priceId = resolvePriceId(intent);
            Subscription stripeSub = createStripeSubscription(customerId, priceId, paymentMethodId, user, setupIntentId);

            UserSubscription saved = syncService.persist(stripeSub);
            if (saved == null) {
                throw new BillingException("La synchronisation de l'abonnement a échoué.");
            }
            return saved;
        } catch (StripeException e) {
            throw new BillingException("Création de l'abonnement impossible: " + e.getMessage(), e);
        }
    }

    // In-app plan change: swaps the live subscription's price (with proration), reusing the
    // existing payment method. No new SetupIntent/checkout needed. Re-activates a subscription
    // that was flagged to cancel at period end, since choosing a plan expresses intent to keep it.
    public UserSubscription changePlan(String userEmail, PlanTier tier, BillingCycle cycle) {
        properties.requireSecretKey();
        assertNotStandaloneAddon(tier);
        User user = userRepository.getByEmail(userEmail);
        UserSubscription current = subscriptionRepository.findByUserId(user.getId())
                .filter(s -> StripeSubscriptionStatus.isLive(s.getStatus()))
                .orElseThrow(() -> new BillingValidationException(
                        "Aucun abonnement actif à modifier. Souscrivez d'abord un plan."));
        String subscriptionId = current.getStripeSubscriptionId();
        if (subscriptionId == null) {
            throw new BillingValidationException("Abonnement Stripe introuvable pour ce compte.");
        }
        String newPriceId = catalogService.priceId(tier, cycle);
        if (newPriceId.equals(current.getStripePriceId())) {
            throw new BillingValidationException("Vous êtes déjà sur ce plan.");
        }
        try {
            Subscription stripeSub = Subscription.retrieve(subscriptionId);
            List<SubscriptionItem> items = stripeSub.getItems() != null ? stripeSub.getItems().getData() : null;
            if (items == null || items.isEmpty()) {
                throw new BillingException("L'abonnement Stripe n'a aucune ligne à modifier.");
            }
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(items.get(0).getId())
                            .setPrice(newPriceId)
                            .build())
                    .setCancelAtPeriodEnd(false)
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.ALLOW_INCOMPLETE)
                    .putMetadata("user_id", user.getId().toString())
                    .build();
            Subscription updated = stripeSub.update(params);
            UserSubscription saved = syncService.persist(updated);
            if (saved == null) {
                throw new BillingException("La synchronisation de l'abonnement a échoué.");
            }
            return saved;
        } catch (StripeException e) {
            throw new BillingException("Changement de plan impossible: " + e.getMessage(), e);
        }
    }

    // Résiliation / reprise IN-APP (sans passer par le portail Stripe). On ne résilie PAS
    // immédiatement : cancel_at_period_end laisse l'accès jusqu'à la fin de la période déjà payée,
    // puis l'abonnement s'éteint. Reprendre (resume) = lever ce drapeau avant l'échéance.
    // (Un changement de plan lève aussi le drapeau — cf. changePlan.)
    public UserSubscription setCancelAtPeriodEnd(String userEmail, boolean cancelAtPeriodEnd) {
        properties.requireSecretKey();
        User user = userRepository.getByEmail(userEmail);
        UserSubscription current = subscriptionRepository.findByUserId(user.getId())
                .filter(s -> StripeSubscriptionStatus.isLive(s.getStatus()))
                .orElseThrow(() -> new BillingValidationException(
                        "Aucun abonnement actif à " + (cancelAtPeriodEnd ? "résilier" : "reprendre") + "."));
        String subscriptionId = current.getStripeSubscriptionId();
        if (subscriptionId == null) {
            throw new BillingValidationException("Abonnement Stripe introuvable pour ce compte.");
        }
        if (current.isCancelAtPeriodEnd() == cancelAtPeriodEnd) {
            // Déjà dans l'état demandé : rien à faire, on renvoie l'état courant (idempotent).
            return current;
        }
        try {
            Subscription stripeSub = Subscription.retrieve(subscriptionId);
            Subscription updated = stripeSub.update(SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(cancelAtPeriodEnd)
                    .build());
            UserSubscription saved = syncService.persist(updated);
            if (saved == null) {
                throw new BillingException("La synchronisation de l'abonnement a échoué.");
            }
            return saved;
        } catch (StripeException e) {
            throw new BillingException(
                    (cancelAtPeriodEnd ? "Résiliation" : "Reprise") + " de l'abonnement impossible: "
                            + e.getMessage(), e);
        }
    }

    // ATELIER+ (add-on) : ajoute ou retire une 2e ligne (price ATELIER_PLUS) sur l'abonnement de base
    // existant, avec proration. Requiert un abonnement ATELIER live (l'option n'a pas de sens seule).
    public UserSubscription setAtelierPlus(String userEmail, boolean enable) {
        properties.requireSecretKey();
        User user = userRepository.getByEmail(userEmail);
        UserSubscription current = subscriptionRepository.findByUserId(user.getId())
                .filter(s -> StripeSubscriptionStatus.isLive(s.getStatus()))
                .orElseThrow(() -> new BillingValidationException(
                        "ATELIER+ est une option d'un abonnement ATELIER actif — souscrivez d'abord un plan."));
        String subscriptionId = current.getStripeSubscriptionId();
        if (subscriptionId == null) {
            throw new BillingValidationException("Abonnement Stripe introuvable pour ce compte.");
        }
        BillingCycle cycle = current.getBillingCycle() != null ? current.getBillingCycle() : BillingCycle.MONTHLY;
        String plusMonthly = catalogService.priceId(PlanTier.ATELIER_PLUS, BillingCycle.MONTHLY);
        String plusAnnual = catalogService.priceId(PlanTier.ATELIER_PLUS, BillingCycle.ANNUAL);
        try {
            Subscription stripeSub = Subscription.retrieve(subscriptionId);
            List<SubscriptionItem> items = stripeSub.getItems() != null ? stripeSub.getItems().getData() : List.of();
            SubscriptionItem plusItem = items.stream()
                    .filter(it -> it.getPrice() != null
                            && (it.getPrice().getId().equals(plusMonthly) || it.getPrice().getId().equals(plusAnnual)))
                    .findFirst().orElse(null);

            if (enable == (plusItem != null)) {
                return current; // Déjà dans l'état voulu (idempotent).
            }

            SubscriptionUpdateParams.Builder params = SubscriptionUpdateParams.builder()
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.ALLOW_INCOMPLETE)
                    .putMetadata("user_id", user.getId().toString());
            if (enable) {
                params.addItem(SubscriptionUpdateParams.Item.builder()
                        .setPrice(catalogService.priceId(PlanTier.ATELIER_PLUS, cycle)).build());
            } else {
                params.addItem(SubscriptionUpdateParams.Item.builder()
                        .setId(plusItem.getId()).setDeleted(true).build());
            }
            Subscription updated = stripeSub.update(params.build());
            UserSubscription saved = syncService.persist(updated);
            if (saved == null) {
                throw new BillingException("La synchronisation de l'abonnement a échoué.");
            }
            return saved;
        } catch (StripeException e) {
            throw new BillingException("Mise à jour de l'option ATELIER+ impossible: " + e.getMessage(), e);
        }
    }

    // Make the confirmed payment method the customer's default, so Stripe bills it for the subscription.
    private void setCustomerDefaultPaymentMethod(String customerId, String paymentMethodId) throws StripeException {
        Customer customer = Customer.retrieve(customerId);
        customer.update(CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId)
                        .build())
                .build());
    }

    // Idempotent on (user, setupIntent) so a retried confirmation never creates a duplicate subscription.
    private Subscription createStripeSubscription(String customerId, String priceId, String paymentMethodId,
                                                  User user, String setupIntentId) throws StripeException {
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).build())
                .setDefaultPaymentMethod(paymentMethodId)
                .putMetadata("user_id", user.getId().toString())
                .setPaymentSettings(SubscriptionCreateParams.PaymentSettings.builder()
                        .setSaveDefaultPaymentMethod(
                                SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION)
                        .build())
                .build();
        RequestOptions idempotent = RequestOptions.builder()
                .setIdempotencyKey("sub-create:" + user.getId() + ":" + setupIntentId)
                .build();
        return Subscription.create(params, idempotent);
    }

    private String resolvePriceId(SetupIntent intent) {
        String priceId = intent.getMetadata().get("price_id");
        if (priceId == null) {
            PlanTier tier = PlanTier.fromKey(intent.getMetadata().get("tier")).orElse(null);
            BillingCycle cycle = BillingCycle.fromKey(intent.getMetadata().get("cycle"));
            if (tier != null) {
                priceId = catalogService.priceId(tier, cycle);
            }
        }
        if (priceId == null) {
            throw new BillingValidationException("Plan introuvable pour cette confirmation.");
        }
        return priceId;
    }

    public SubscriptionStateResponse getState(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        UserSubscription current = getCurrent(user);
        QuotaService.Quota quota = quotaService.forUser(user);
        return new SubscriptionStateResponse(
                SubscriptionResponse.from(current),
                QuotaResponse.from(quota),
                quota.hasActiveSubscription(),
                current != null && StripeSubscriptionStatus.isLive(current.getStatus()),
                properties.publishableKey()
        );
    }

    public UserSubscription getCurrent(User user) {
        UserSubscription entity = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        if (entity == null || !needsLiveRefresh(entity)) {
            return entity;
        }
        try {
            // Network call outside any transaction; persistence happens in syncService.
            Subscription stripeSub = Subscription.retrieve(entity.getStripeSubscriptionId());
            UserSubscription synced = syncService.persist(stripeSub);
            return synced != null ? synced : entity;
        } catch (StripeException e) {
            log.debug("Live subscription refresh failed for {}: {}",
                    entity.getStripeSubscriptionId(), e.getMessage());
            return entity;
        }
    }

    // Without webhooks (typically local dev), refresh the row live so the state stays accurate.
    private boolean needsLiveRefresh(UserSubscription entity) {
        return entity.getStripeSubscriptionId() != null
                && properties.hasSecretKey()
                && !properties.hasWebhookSecret();
    }

    public String createCheckoutSession(String userEmail, PlanTier tier, BillingCycle cycle) {
        properties.requireSecretKey();
        assertNotStandaloneAddon(tier);
        User user = userRepository.getByEmail(userEmail);
        subscriptionRepository.findByUserId(user.getId())
                .filter(s -> StripeSubscriptionStatus.isLive(s.getStatus()))
                .ifPresent(s -> {
                    throw new ConflictException(
                            "Vous avez déjà un abonnement en cours. Utilisez le portail pour le modifier.");
                });
        String customerId = customerService.ensureCustomer(user);
        String priceId = catalogService.priceId(tier, cycle);
        String returnUrl = properties.portalReturnUrl();
        return StripeCalls.billed("Ouverture du paiement Stripe impossible", () -> {
            com.stripe.param.checkout.SessionCreateParams params =
                    com.stripe.param.checkout.SessionCreateParams.builder()
                            .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                            .setCustomer(customerId)
                            .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build())
                            .setSuccessUrl(appendQuery(returnUrl, "checkout=success"))
                            .setCancelUrl(appendQuery(returnUrl, "checkout=cancel"))
                            .putMetadata("user_id", user.getId().toString())
                            .putMetadata("tier", tier.key())
                            .putMetadata("cycle", cycle.key())
                            .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                                    .putMetadata("user_id", user.getId().toString())
                                    .build())
                            .build();
            return com.stripe.model.checkout.Session.create(params).getUrl();
        });
    }

    private static String appendQuery(String url, String query) {
        return url.contains("?") ? url + "&" + query : url + "?" + query;
    }

    public String createPortalSession(String userEmail) {
        properties.requireSecretKey();
        User user = userRepository.getByEmail(userEmail);
        String customerId = customerService.ensureCustomer(user);
        return StripeCalls.billed("Ouverture du portail client impossible", () -> {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(properties.portalReturnUrl())
                    .build();
            return Session.create(params).getUrl();
        });
    }

    public void resyncById(String subscriptionId) {
        if (subscriptionId == null || !properties.hasSecretKey()) {
            return;
        }
        try {
            Subscription stripeSub = Subscription.retrieve(subscriptionId);
            syncService.persist(stripeSub);
        } catch (StripeException e) {
            log.warn("Could not resync subscription {}: {}", subscriptionId, e.getMessage());
        }
    }
}
