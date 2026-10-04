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

@Service
@RequiredArgsConstructor
public class AtelierPlusResolver {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public boolean isAtelierPlus(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .filter(UserSubscription::isActive)
                .map(UserSubscription::isAtelierPlus)
                .orElse(false);
    }

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
