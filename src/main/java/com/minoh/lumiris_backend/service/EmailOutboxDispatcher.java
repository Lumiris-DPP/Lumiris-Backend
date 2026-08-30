package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.EmailOutbox;
import com.minoh.lumiris_backend.entity.EmailOutboxStatus;
import com.minoh.lumiris_backend.repository.EmailOutboxRepository;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.List;

// Consomme email_outbox : rend le template Thymeleaf et envoie via Resend. Un échec avance
// next_attempt_at avec un backoff exponentiel (1, 2, 4, 8 min...) ; au-delà de max_attempts la
// ligne passe DEAD (DLQ) et n'est plus reprise automatiquement — elle reste en base pour l'admin.
//
// Instance unique supposée : deux dispatchers concurrents pourraient lire et envoyer la même ligne
// PENDING en double (pas de SELECT ... FOR UPDATE SKIP LOCKED ici). À revoir si le backend passe
// un jour multi-instance.
@Component
@RequiredArgsConstructor
public class EmailOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDispatcher.class);
    private static final long POLL_DELAY_MS = 30_000L;
    private static final int BATCH_SIZE = 50;
    private static final long BACKOFF_BASE_SECONDS = 60L;

    private final EmailOutboxRepository emailOutboxRepository;
    private final TemplateEngine templateEngine;
    private final Resend resend;

    @Value("${app.mail.from}")
    private String from;

    @Scheduled(fixedDelay = POLL_DELAY_MS, initialDelay = POLL_DELAY_MS)
    public void dispatch() {
        List<EmailOutbox> due = emailOutboxRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                EmailOutboxStatus.PENDING, Instant.now(), PageRequest.of(0, BATCH_SIZE));
        for (EmailOutbox email : due) {
            attempt(email);
        }
    }

    private void attempt(EmailOutbox email) {
        try {
            Context context = new Context();
            context.setVariables(email.getVariables());
            String html = templateEngine.process(email.getTemplate(), context);

            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(from)
                    .to(email.getRecipientEmail())
                    .subject(email.getSubject())
                    .html(html)
                    .build();
            resend.emails().send(options);

            email.setStatus(EmailOutboxStatus.SENT);
            email.setSentAt(Instant.now());
        } catch (ResendException | RuntimeException e) {
            onFailure(email, e);
        }
        emailOutboxRepository.save(email);
    }

    private void onFailure(EmailOutbox email, Exception e) {
        email.setAttempts(email.getAttempts() + 1);
        email.setLastError(truncate(e.getMessage()));

        if (email.getAttempts() >= email.getMaxAttempts()) {
            email.setStatus(EmailOutboxStatus.DEAD);
            log.error("Email {} vers {} passé en DLQ après {} tentatives : {}",
                    email.getTemplate(), email.getRecipientEmail(), email.getAttempts(), e.getMessage());
            return;
        }

        long backoffSeconds = BACKOFF_BASE_SECONDS * (1L << (email.getAttempts() - 1));
        email.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
        log.warn("Échec envoi email {} vers {} (tentative {}/{}), retry dans {}s : {}",
                email.getTemplate(), email.getRecipientEmail(), email.getAttempts(),
                email.getMaxAttempts(), backoffSeconds, e.getMessage());
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
