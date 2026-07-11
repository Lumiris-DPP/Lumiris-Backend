package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.domain.BillingCycle;
import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.domain.StripeSubscriptionStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Mirrors a Stripe subscription onto our {@link UserSubscription} row. Isolated from
 * {@link SubscriptionService} so the DB write stays in a short transaction while the
 * network calls that fetch the Stripe object happen outside any transaction.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSyncService.class);

    private final StripeProperties properties;
    private final StripeCatalogService catalogService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserSubscription persist(Subscription stripeSub) {
        String customerId = stripeSub.getCustomer();
        User user = userRepository.findByStripeCustomerId(customerId).orElse(null);
        if (user == null) {
            log.warn("Webhook subscription {} references unknown customer {} — skipped.",
                    stripeSub.getId(), customerId);
            return null;
        }

        UserSubscription entity = resolveRow(user, stripeSub);
        if (entity == null) {
            return subscriptionRepository.findByUserId(user.getId()).orElse(null);
        }

        entity.setUser(user);
        entity.setStripeSubscriptionId(stripeSub.getId());
        entity.setStripeCustomerId(customerId);
        entity.setStatus(stripeSub.getStatus());
        entity.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));
        Long periodEnd = stripeSub.getCurrentPeriodEnd();
        entity.setCurrentPeriodEnd(periodEnd != null ? Instant.ofEpochSecond(periodEnd) : null);

        applyPlanFromItems(entity, stripeSub);

        return subscriptionRepository.save(entity);
    }

    private void applyPlanFromItems(UserSubscription entity, Subscription stripeSub) {
        if (stripeSub.getItems() == null) {
            return;
        }
        List<SubscriptionItem> items = stripeSub.getItems().getData();
        if (items == null || items.isEmpty() || items.get(0).getPrice() == null) {
            return;
        }
        var price = items.get(0).getPrice();
        entity.setStripePriceId(price.getId());
        // Cache-only: this runs inside the persist() transaction, so it must not call Stripe.
        // On a cache miss we derive tier/cycle from the price object already loaded with the subscription.
        catalogService.cachedCoordinate(price.getId()).ifPresentOrElse(
                coord -> {
                    entity.setPlanTier(coord.tier());
                    entity.setBillingCycle(coord.cycle());
                },
                () -> {
                    PlanTier.fromProductId(price.getProduct(), properties.products())
                            .ifPresent(entity::setPlanTier);
                    if (price.getRecurring() != null) {
                        entity.setBillingCycle(BillingCycle.fromStripeInterval(price.getRecurring().getInterval()));
                    }
                });
    }

    private UserSubscription resolveRow(User user, Subscription stripeSub) {
        UserSubscription byId = subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).orElse(null);
        if (byId != null) {
            return byId;
        }
        UserSubscription byUser = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        if (byUser == null) {
            return new UserSubscription();
        }
        if (byUser.getStripeSubscriptionId() == null) {
            return byUser;
        }
        boolean incomingActive = StripeSubscriptionStatus.isActive(stripeSub.getStatus());
        if (!incomingActive && byUser.isActive()) {
            log.info("Ignoring stale subscription {} ({}) — user keeps active subscription {}.",
                    stripeSub.getId(), stripeSub.getStatus(), byUser.getStripeSubscriptionId());
            return null;
        }
        return byUser;
    }
}
