package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.entity.PushSubscription;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;
import java.util.Map;

// Envoie une notification push à tous les abonnements (navigateurs/appareils) d'un utilisateur.
// PushService est construit paresseusement : sans clés VAPID configurées, le service s'efface
// silencieusement — même logique que MailService/Resend sans clé API.
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    static {
        // Le provider "BC" est requis par webpush-java (KeyFactory.getInstance("EC", "BC")) et
        // n'est enregistré nulle part ailleurs dans l'app — l'enregistrement est idempotent.
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${webpush.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${webpush.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${webpush.vapid.subject:mailto:contact@lumiris.app}")
    private String vapidSubject;

    private PushService pushService;
    private boolean initialized = false;

    public void send(User recipient, String title, String body, String href) {
        PushService service = pushServiceOrNull();
        if (service == null) {
            return;
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findByUser_Id(recipient.getId());
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = buildPayload(title, body, href);
        subscriptions.forEach(subscription -> sendTo(service, subscription, payload));
    }

    private void sendTo(PushService service, PushSubscription subscription, String payload) {
        try {
            Subscription webPushSubscription = new Subscription(subscription.getEndpoint(),
                    new Subscription.Keys(subscription.getP256dh(), subscription.getAuth()));
            HttpResponse response = service.send(new Notification(webPushSubscription, payload));
            int status = response.getStatusLine().getStatusCode();
            // 404/410 : le navigateur a révoqué cet abonnement (désinstallation, expiration...) —
            // il ne sert plus à rien de le garder.
            if (status == 404 || status == 410) {
                subscriptionRepository.delete(subscription);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Push non envoyé (abonnement {}): {}", subscription.getId(), e.getMessage());
        }
    }

    private String buildPayload(String title, String body, String href) {
        try {
            return objectMapper.writeValueAsString(Map.of("title", title, "body", body, "url", href == null ? "" : href));
        } catch (Exception e) {
            return "{}";
        }
    }

    private synchronized PushService pushServiceOrNull() {
        if (!initialized) {
            initialized = true;
            if (!vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank()) {
                try {
                    pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
                } catch (Exception e) {
                    log.error("Clés VAPID invalides, Web Push désactivé : {}", e.getMessage());
                }
            }
        }
        return pushService;
    }
}
