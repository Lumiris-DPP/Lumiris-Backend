package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.PushSubscriptionRequest;
import com.minoh.lumiris_backend.entity.PushSubscription;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.PushSubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Value("${webpush.vapid.public-key:}")
    private String vapidPublicKey;

    // Vide si non configuré — le front traite ça comme « Web Push indisponible » plutôt que
    // d'afficher un opt-in qui échouerait à l'abonnement.
    public String vapidPublicKey() {
        return vapidPublicKey;
    }

    // L'endpoint est la clé d'idempotence du navigateur : un re-abonnement (même appareil, ou
    // même endpoint réattribué après un changement de compte sur un appareil partagé) met à jour
    // la ligne existante plutôt que d'en créer une seconde.
    @Transactional
    public void subscribe(String userEmail, PushSubscriptionRequest request) {
        User user = userRepository.getByEmail(userEmail);
        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        subscription.setUser(user);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(String userEmail, String endpoint) {
        User user = userRepository.getByEmail(userEmail);
        subscriptionRepository.deleteByUser_IdAndEndpoint(user.getId(), endpoint);
    }
}
