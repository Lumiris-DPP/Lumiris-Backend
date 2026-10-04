package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.repository.SellerAccountRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Identifie les ateliers pouvant recevoir un paiement.
@Service
@RequiredArgsConstructor
public class PayableSellerResolver {

    private final SellerAccountRepository sellerAccountRepository;
    private final SubscriptionRepository subscriptionRepository;

    // Identifie les comptes autorisés à recevoir un paiement.
    @Transactional(readOnly = true)
    public Set<UUID> payableUserIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> chargeable = sellerAccountRepository.payableUserIds(userIds);
        if (chargeable.isEmpty()) {
            return Set.of();
        }
        return subscriptionRepository.findByUserIdIn(chargeable).stream()
                .filter(UserSubscription::isActive)
                .map(s -> s.getUser().getId())
                .collect(Collectors.toSet());
    }
}
