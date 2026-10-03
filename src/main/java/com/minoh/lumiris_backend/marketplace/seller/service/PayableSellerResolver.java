package com.minoh.lumiris_backend.marketplace.seller.service;

import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.marketplace.seller.repository.SellerAccountRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Vendeurs dont les pièces sont réellement achetables : Stripe Connect encaissable ET abonnement
// ATELIER actif. Un produit publié par un artisan qui ne remplit pas les deux reste invisible côté
// acheteur — on évite le cul-de-sac « impossible d'encaisser » au moment du paiement.
@Service
@RequiredArgsConstructor
public class PayableSellerResolver {

    private final SellerAccountRepository sellerAccountRepository;
    private final SubscriptionRepository subscriptionRepository;

    /** Retient les vendeurs capables d’encaisser avec un abonnement admissible. */
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
