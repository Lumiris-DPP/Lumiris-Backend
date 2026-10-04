package com.minoh.lumiris_backend.marketplace.seller.service;

import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Identifie les ateliers disposant de l'abonnement Atelier Plus.
@Service
@RequiredArgsConstructor
public class AtelierPlusResolver {

    private final SubscriptionRepository subscriptionRepository;

    // Vérifie l'abonnement Atelier Plus actif d'un compte.
    @Transactional(readOnly = true)
    public boolean isAtelierPlus(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .filter(UserSubscription::isActive)
                .map(UserSubscription::isAtelierPlus)
                .orElse(false);
    }

    // Identifie les comptes disposant d'un abonnement Atelier Plus actif.
    @Transactional(readOnly = true)
    public Set<UUID> atelierPlusUserIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return subscriptionRepository.findByUserIdIn(userIds).stream()
                .filter(UserSubscription::isActive)
                .filter(UserSubscription::isAtelierPlus)
                .map(s -> s.getUser().getId())
                .collect(Collectors.toSet());
    }
}
