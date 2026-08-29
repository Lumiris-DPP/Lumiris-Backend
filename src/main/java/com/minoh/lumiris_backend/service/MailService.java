package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.EmailOutboxResponse;
import com.minoh.lumiris_backend.entity.EmailOutbox;
import com.minoh.lumiris_backend.entity.EmailOutboxStatus;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Point d'entrée métier des emails transactionnels. Ne parle jamais à Resend directement : chaque
// appel écrit une ligne PENDING dans email_outbox (dans la transaction de l'appelant), et c'est
// EmailOutboxDispatcher qui rend le template et envoie, avec retry/DLQ.
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final int DEAD_PAGE_SIZE = 30;

    private final EmailOutboxRepository emailOutboxRepository;

    public void sendRegistrationPending(String to, String name) {
        String subject = "Votre inscription est en cours de validation";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        send(to, subject, "email/registration-pending", context);
    }

    public void sendVerified(String to, String name) {
        String subject = "Bienvenue sur le réseau Lumiris !";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        send(to, subject, "email/registration-verified", context);
    }

    public void sendRejected(String to, String name, String reason) {
        String subject = "Votre inscription Lumiris n'a pas pu être validée";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        context.setVariable("reason", reason);
        send(to, subject, "email/registration-rejected", context);
    }

    public void sendCertificateExpiring(String to, String name, String certificateName, String expiryDate) {
        String subject = "Votre certificat arrive à expiration";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        context.setVariable("certificateName", certificateName);
        context.setVariable("expiryDate", expiryDate);
        send(to, subject, "email/certificate-expiring", context);
    }

    public void sendRetouchAccepted(String to, String name, String itemName) {
        String subject = "Votre retouche a été acceptée";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        context.setVariable("itemName", itemName);
        send(to, subject, "email/retouch-accepted", context);
    }

    public void sendPaymentSuccess(String to, String name, String amount, String orderRef) {
        String subject = "Paiement confirmé";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        context.setVariable("amount", amount);
        context.setVariable("orderRef", orderRef);
        send(to, subject, "email/payment-success", context);
    }

    public void sendPaymentFailed(String to, String name, String amount, String orderRef) {
        String subject = "Échec du paiement";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        context.setVariable("amount", amount);
        context.setVariable("orderRef", orderRef);
        send(to, subject, "email/payment-failed", context);
    }

    public void sendPassportPublished(String to, String name, String passportName, String passportUrl) {
        String subject = "Votre passeport produit est publié";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        context.setVariable("passportName", passportName);
        context.setVariable("passportUrl", passportUrl);
        send(to, subject, "email/passport-published", context);
    }

    public void sendPassportScanned(String to, String name, String passportName, String passportUrl) {
        String subject = "Votre passeport produit a été scanné";
        Context context = titledContext(subject);
        context.setVariable("name", name);
        context.setVariable("passportName", passportName);
        context.setVariable("passportUrl", passportUrl);
        send(to, subject, "email/passport-scanned", context);
    }

    public void sendKybIncomplete(String to, String name, String note) {
        String body = "Bonjour " + name + ",\n\nVotre dossier KYB Lumiris est incomplet et doit être complété avant de pouvoir être validé.";
        if (note != null && !note.isBlank()) {
            body += "\n\nDétail : " + note;
        }
        body += "\n\nConnectez-vous à votre espace pour le mettre à jour.\nL'équipe Lumiris";
        send(to, "Votre dossier KYB Lumiris est incomplet", body);
    }

    public void sendRepairRequestRefused(String to, String name, String productName) {
        send(to, "Devis refusé",
                "Bonjour " + name + ",\n\nLe client a refusé votre devis pour \"" + productName + "\". " +
                "La demande est désormais close.\n\nL'équipe Lumiris");
    }

    public void sendNotification(String to, String title, String body) {
        Context context = titledContext(title);
        context.setVariable("body", body);
        send(to, title, "email/notification", context);
    }

    // DLQ : lignes qu'EmailOutboxDispatcher a abandonnées après max_attempts échecs.
    public List<EmailOutboxResponse> listDead(int page) {
        return emailOutboxRepository
                .findByStatusOrderByCreatedAtDesc(EmailOutboxStatus.DEAD, PageRequest.of(page, DEAD_PAGE_SIZE))
                .stream().map(EmailOutboxResponse::from).toList();
    }

    // Relance manuelle d'une ligne DLQ : repart en PENDING, immédiatement due, tentatives à zéro.
    // last_error reste tel quel jusqu'au prochain passage du dispatcher — utile pour comparer
    // l'échec précédent au nouveau si ça retombe en DEAD.
    public EmailOutboxResponse retryDead(UUID id) {
        EmailOutbox outbox = emailOutboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Email introuvable : " + id));
        if (outbox.getStatus() != EmailOutboxStatus.DEAD) {
            throw new ConflictException("Cet email n'est pas en DLQ (statut actuel : " + outbox.getStatus() + ")");
        }
        outbox.setStatus(EmailOutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(Instant.now());
        return EmailOutboxResponse.from(emailOutboxRepository.save(outbox));
    }

    private Context titledContext(String title) {
        Context context = new Context();
        context.setVariable("title", title);
        return context;
    }

    private void send(String to, String subject, String template, Context context) {
        EmailOutbox outbox = new EmailOutbox();
        outbox.setRecipientEmail(to);
        outbox.setSubject(subject);
        outbox.setTemplate(template);
        outbox.setVariables(toMap(context));
        emailOutboxRepository.save(outbox);
    }

    private Map<String, Object> toMap(Context context) {
        Map<String, Object> variables = new HashMap<>();
        for (String name : context.getVariableNames()) {
            variables.put(name, context.getVariable(name));
        }
        return variables;
    }
}
