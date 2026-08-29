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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Notifications in-app + email + push. Une transition de commande appelle `notify` une fois par
// destinataire ; l'écriture en base fait foi, l'email et le push ne sont que des rappels
// best-effort (une panne SMTP/Web Push ne doit jamais faire échouer — ni annuler — la transition
// métier qui l'a déclenché).
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

    @Transactional
    public void notify(User recipient, NotificationType type, String title, String body,
                       String href, MarketplaceOrder order) {
        if (recipient == null) {
            return;
        }
        save(recipient, type, title, body, href, order);
        sendMail(recipient, type, () -> mailService.sendNotification(recipient.getEmail(), title, body));
        sendPush(recipient, type, title, body, href);
    }

    @Transactional
    public void notifyCertificateExpiring(User recipient, String certificateName, String expiryDate) {
        if (recipient == null) {
            return;
        }
        String title = "Certificat bientôt expiré";
        String body = "Votre certificat " + certificateName + " arrive à expiration le " + expiryDate + ".";
        save(recipient, NotificationType.CERTIFICATE_EXPIRING, title, body, null, null);
        sendMail(recipient, NotificationType.CERTIFICATE_EXPIRING, () ->
                mailService.sendCertificateExpiring(recipient.getEmail(), recipient.getName(), certificateName, expiryDate));
        sendPush(recipient, NotificationType.CERTIFICATE_EXPIRING, title, body, null);
    }

    @Transactional
    public void notifyRetouchAccepted(User recipient, String itemName, String href) {
        if (recipient == null) {
            return;
        }
        String title = "Retouche acceptée";
        String body = "Votre demande de retouche pour " + itemName + " a été acceptée.";
        save(recipient, NotificationType.RETOUCH_ACCEPTED, title, body, href, null);
        sendMail(recipient, NotificationType.RETOUCH_ACCEPTED, () ->
                mailService.sendRetouchAccepted(recipient.getEmail(), recipient.getName(), itemName));
        sendPush(recipient, NotificationType.RETOUCH_ACCEPTED, title, body, href);
    }

    @Transactional
    public void notifyPaymentSuccess(User recipient, MarketplaceOrder order) {
        if (recipient == null) {
            return;
        }
        String amount = formatAmount(order);
        String orderRef = orderRef(order);
        String title = "Paiement confirmé";
        String body = "Votre paiement de " + amount + " pour la commande " + orderRef + " a bien été validé.";
        save(recipient, NotificationType.PAYMENT_SUCCEEDED, title, body, null, order);
        sendMail(recipient, NotificationType.PAYMENT_SUCCEEDED, () ->
                mailService.sendPaymentSuccess(recipient.getEmail(), recipient.getName(), amount, orderRef));
        sendPush(recipient, NotificationType.PAYMENT_SUCCEEDED, title, body, null);
    }

    @Transactional
    public void notifyPaymentFailed(User recipient, MarketplaceOrder order) {
        if (recipient == null) {
            return;
        }
        String amount = formatAmount(order);
        String orderRef = orderRef(order);
        String title = "Échec du paiement";
        String body = "Le paiement de " + amount + " pour la commande " + orderRef + " n'a pas pu être traité.";
        save(recipient, NotificationType.PAYMENT_FAILED, title, body, null, order);
        sendMail(recipient, NotificationType.PAYMENT_FAILED, () ->
                mailService.sendPaymentFailed(recipient.getEmail(), recipient.getName(), amount, orderRef));
        sendPush(recipient, NotificationType.PAYMENT_FAILED, title, body, null);
    }

    @Transactional
    public void notifyPassportPublished(User recipient, String passportName, String passportUrl) {
        if (recipient == null) {
            return;
        }
        String title = "Passeport produit publié";
        String body = "Le passeport produit " + passportName + " est désormais publié.";
        save(recipient, NotificationType.PASSPORT_PUBLISHED, title, body, passportUrl, null);
        sendMail(recipient, NotificationType.PASSPORT_PUBLISHED, () ->
                mailService.sendPassportPublished(recipient.getEmail(), recipient.getName(), passportName, passportUrl));
        sendPush(recipient, NotificationType.PASSPORT_PUBLISHED, title, body, passportUrl);
    }

    @Transactional
    public void notifyPassportScanned(User recipient, String passportName, String passportUrl) {
        if (recipient == null) {
            return;
        }
        String title = "Passeport produit scanné";
        String body = "Votre passeport produit " + passportName + " vient d'être scanné.";
        save(recipient, NotificationType.PASSPORT_SCANNED, title, body, passportUrl, null);
        sendMail(recipient, NotificationType.PASSPORT_SCANNED, () ->
                mailService.sendPassportScanned(recipient.getEmail(), recipient.getName(), passportName, passportUrl));
        sendPush(recipient, NotificationType.PASSPORT_SCANNED, title, body, passportUrl);
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
