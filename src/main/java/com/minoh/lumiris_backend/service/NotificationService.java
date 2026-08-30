package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.NotificationResponse;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.Notification;
import com.minoh.lumiris_backend.entity.NotificationType;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.NotificationRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Notifications in-app + email + push. Une transition métier appelle `notify*` une fois par
// destinataire. Chaque appel tourne dans sa PROPRE transaction (REQUIRES_NEW), toute exception
// (écriture en base, email, push) y est avalée : une panne côté notifications ne doit jamais faire
// échouer — ni annuler — la transaction métier qui l'a déclenché (ex. publication d'un passeport).
// Contrepartie assumée : si l'appelant échoue juste après l'appel, la notification reste écrite
// malgré tout — cas rare, sans commune mesure avec bloquer l'action métier pour un souci de notif.
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DEFAULT_PAGE_SIZE = 30;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final PushNotificationService pushNotificationService;
    private final NotificationPreferenceService preferenceService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(User recipient, NotificationType type, String title, String body,
                       String href, MarketplaceOrder order) {
        if (recipient == null) {
            return;
        }
        try {
            save(recipient, type, title, body, href, order);
            sendMail(recipient, type, () -> mailService.sendNotification(recipient.getEmail(), title, body));
            sendPush(recipient, type, title, body, href);
        } catch (RuntimeException e) {
            log.warn("Notification {} non traitée pour {}: {}", type, recipient.getId(), e.getMessage());
        }
    }

    // Commande marketplace : gardé pour un futur appelant (ORDER_PAID couvre déjà cet événement
    // avec plus de contexte aujourd'hui — voir OrderLifecycleService.markPaid). Le seul appelant
    // actuel est la surcharge par abonnement ci-dessous, sans commande à lier.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPaymentSuccess(User recipient, MarketplaceOrder order) {
        notifyPaymentSuccess(recipient, formatAmount(order), orderRef(order), order);
    }

    // Paiement d'abonnement (facture Stripe) : pas de MarketplaceOrder à lier.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPaymentSuccess(User recipient, String amount, String reference) {
        notifyPaymentSuccess(recipient, amount, reference, null);
    }

    private void notifyPaymentSuccess(User recipient, String amount, String reference, MarketplaceOrder order) {
        if (recipient == null) {
            return;
        }
        try {
            String title = "Paiement confirmé";
            String body = "Votre paiement de " + amount + " (réf. " + reference + ") a bien été validé.";
            save(recipient, NotificationType.PAYMENT_SUCCEEDED, title, body, null, order);
            sendMail(recipient, NotificationType.PAYMENT_SUCCEEDED, () ->
                    mailService.sendPaymentSuccess(recipient.getEmail(), recipient.getName(), amount, reference));
            sendPush(recipient, NotificationType.PAYMENT_SUCCEEDED, title, body, null);
        } catch (RuntimeException e) {
            log.warn("Notification PAYMENT_SUCCEEDED non traitée pour {}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPaymentFailed(User recipient, MarketplaceOrder order) {
        notifyPaymentFailed(recipient, formatAmount(order), orderRef(order), order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPaymentFailed(User recipient, String amount, String reference) {
        notifyPaymentFailed(recipient, amount, reference, null);
    }

    private void notifyPaymentFailed(User recipient, String amount, String reference, MarketplaceOrder order) {
        if (recipient == null) {
            return;
        }
        try {
            String title = "Échec du paiement";
            String body = "Le paiement de " + amount + " (réf. " + reference + ") n'a pas pu être traité.";
            save(recipient, NotificationType.PAYMENT_FAILED, title, body, null, order);
            sendMail(recipient, NotificationType.PAYMENT_FAILED, () ->
                    mailService.sendPaymentFailed(recipient.getEmail(), recipient.getName(), amount, reference));
            sendPush(recipient, NotificationType.PAYMENT_FAILED, title, body, null);
        } catch (RuntimeException e) {
            log.warn("Notification PAYMENT_FAILED non traitée pour {}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPassportPublished(User recipient, String passportName, String passportUrl) {
        if (recipient == null) {
            return;
        }
        try {
            String title = "Passeport produit publié";
            String body = "Le passeport produit " + passportName + " est désormais publié.";
            save(recipient, NotificationType.PASSPORT_PUBLISHED, title, body, passportUrl, null);
            sendMail(recipient, NotificationType.PASSPORT_PUBLISHED, () -> mailService.sendPassportPublished(
                    recipient.getEmail(), recipient.getName(), passportName, absoluteUrl(passportUrl)));
            sendPush(recipient, NotificationType.PASSPORT_PUBLISHED, title, body, passportUrl);
        } catch (RuntimeException e) {
            log.warn("Notification PASSPORT_PUBLISHED non traitée pour {}: {}", recipient.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPassportScanned(User recipient, String passportName, String passportUrl) {
        if (recipient == null) {
            return;
        }
        try {
            String title = "Passeport produit scanné";
            String body = "Votre passeport produit " + passportName + " vient d'être scanné.";
            save(recipient, NotificationType.PASSPORT_SCANNED, title, body, passportUrl, null);
            sendMail(recipient, NotificationType.PASSPORT_SCANNED, () -> mailService.sendPassportScanned(
                    recipient.getEmail(), recipient.getName(), passportName, absoluteUrl(passportUrl)));
            sendPush(recipient, NotificationType.PASSPORT_SCANNED, title, body, passportUrl);
        } catch (RuntimeException e) {
            log.warn("Notification PASSPORT_SCANNED non traitée pour {}: {}", recipient.getId(), e.getMessage());
        }
    }

    private void save(User recipient, NotificationType type, String title, String body,
                       String href, MarketplaceOrder order) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setHref(href);
        notification.setOrder(order);
        notificationRepository.save(notification);
    }

    // L'écriture en base fait foi ; une panne d'envoi ne doit jamais remonter à l'appelant, et un
    // désabonnement à la catégorie n'empêche que l'email — pas la Notification in-app déjà écrite.
    private void sendMail(User recipient, NotificationType type, Runnable send) {
        if (!preferenceService.isEmailEnabled(recipient, type)) {
            return;
        }
        try {
            send.run();
        } catch (RuntimeException e) {
            log.warn("Notification {} enregistrée mais email non parti pour {}: {}",
                    type, recipient.getId(), e.getMessage());
        }
    }

    private void sendPush(User recipient, NotificationType type, String title, String body, String href) {
        if (!preferenceService.isPushEnabled(recipient, type)) {
            return;
        }
        try {
            pushNotificationService.send(recipient, title, body, href);
        } catch (RuntimeException e) {
            log.warn("Notification {} enregistrée mais push non parti pour {}: {}",
                    type, recipient.getId(), e.getMessage());
        }
    }

    // Un email n'a pas d'origine à préfixer lui-même — contrairement au href in-app (relatif,
    // préfixé par le front), le lien d'un email doit être absolu pour être cliquable.
    private String absoluteUrl(String relativePath) {
        return frontendUrl + relativePath;
    }

    private String formatAmount(MarketplaceOrder order) {
        return String.format(Locale.FRANCE, "%.2f %s",
                order.getAmountTotalCents() / 100.0, order.getCurrency());
    }

    private String orderRef(MarketplaceOrder order) {
        return order.getInvoiceNumber() != null ? order.getInvoiceNumber() : order.getId().toString();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return notificationRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, DEFAULT_PAGE_SIZE))
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userEmail) {
        return notificationRepository.countByUser_IdAndReadAtIsNull(userRepository.getByEmail(userEmail).getId());
    }

    @Transactional
    public void markRead(String userEmail, UUID notificationId) {
        notificationRepository.markRead(notificationId, userRepository.getByEmail(userEmail).getId(), Instant.now());
    }

    @Transactional
    public void markAllRead(String userEmail) {
        notificationRepository.markAllRead(userRepository.getByEmail(userEmail).getId(), Instant.now());
    }
}
