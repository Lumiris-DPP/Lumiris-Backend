package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Statut « ATELIER+ » = add-on PlanTier.ATELIER_PLUS avec un abonnement actif.
// Source de vérité unique = table subscriptions (jamais ArtisanProfile.plus, non piloté Stripe).
@Service
@RequiredArgsConstructor
public class AtelierPlusResolver {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public boolean isAtelierPlus(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .filter(UserSubscription::isActive)
                .map(s -> s.getPlanTier() == PlanTier.ATELIER_PLUS)
                .orElse(false);
    }

    // Batch : parmi ces utilisateurs, ceux ayant un add-on ATELIER+ actif (évite le N+1).
    @Transactional(readOnly = true)
    public Set<UUID> atelierPlusUserIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return subscriptionRepository.findByUserIdIn(userIds).stream()
                .filter(UserSubscription::isActive)
                .filter(s -> s.getPlanTier() == PlanTier.ATELIER_PLUS)
                .map(s -> s.getUser().getId())
                .collect(Collectors.toSet());
    }
}
