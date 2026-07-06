package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.exception.QuotaExceededException;
import com.minoh.lumiris_backend.exception.SubscriptionRequiredException;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Passport creation requires an active, passport-granting subscription (Solo 50 / Studio 300 / Maison unlimited).
@Service
@RequiredArgsConstructor
public class QuotaService {

    public static final String REASON_SUBSCRIPTION_REQUIRED = "SUBSCRIPTION_REQUIRED";
    public static final String REASON_QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String REASON_PLAN_NOT_ELIGIBLE = "PLAN_NOT_ELIGIBLE";

    private final DppFormRepository dppFormRepository;
    private final SubscriptionRepository subscriptionRepository;

    public record Quota(
            boolean hasActiveSubscription,
            PlanTier tier,
            long used,
            Integer limit,
            boolean unlimited,
            boolean canCreate,
            String reason
    ) {}

    @Transactional(readOnly = true)
    public Quota forUser(User user) {
        long used = dppFormRepository.countByUserId(user.getId());
        UserSubscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        boolean active = sub != null && sub.isActive();
        PlanTier tier = sub != null ? sub.getPlanTier() : null;

        if (!active || tier == null || !tier.grantsPassports()) {
            String reason = active ? REASON_PLAN_NOT_ELIGIBLE : REASON_SUBSCRIPTION_REQUIRED;
            return new Quota(false, tier, used, 0, false, false, reason);
        }
        if (tier.isUnlimited()) {
            return new Quota(true, tier, used, null, true, true, null);
        }
        // Granting + not unlimited ⇒ a finite cap is always present.
        int limit = tier.passportLimit().orElseThrow();
        boolean canCreate = used < limit;
        return new Quota(true, tier, used, limit, false, canCreate, canCreate ? null : REASON_QUOTA_EXCEEDED);
    }

    public void assertCanCreate(User user) {
        // Lock the user's subscription row FOR UPDATE so two simultaneous creations at limit-1 can't
        // both pass the count check (TOCTOU). Runs inside the DppForm create transaction.
        subscriptionRepository.findByUserIdForUpdate(user.getId());
        Quota quota = forUser(user);
        if (!quota.hasActiveSubscription()) {
            throw new SubscriptionRequiredException(
                    "Un abonnement ATELIER actif est requis pour créer un passeport.");
        }
        if (!quota.canCreate()) {
            throw new QuotaExceededException(
                    "Quota de passeports atteint pour le palier " + quota.tier().displayName()
                            + " (" + quota.used() + "/" + quota.limit() + ").");
        }
    }
}
