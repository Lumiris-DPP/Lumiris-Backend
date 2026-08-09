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
import java.util.UUID;

// Notifications in-app + email. Une transition de commande appelle `notify` une fois par
// destinataire ; l'écriture en base fait foi, l'email n'est qu'un rappel best-effort (une panne
// SMTP ne doit jamais faire échouer — ni annuler — la transition métier qui l'a déclenché).
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DEFAULT_PAGE_SIZE = 30;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    public void notify(User recipient, NotificationType type, String title, String body,
                       String href, MarketplaceOrder order) {
        if (recipient == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setHref(href);
        notification.setOrder(order);
        notificationRepository.save(notification);

        try {
            mailService.sendNotification(recipient.getEmail(), title, body);
        } catch (RuntimeException e) {
            log.warn("Notification {} enregistrée mais email non parti pour {}: {}",
                    type, recipient.getId(), e.getMessage());
        }
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
