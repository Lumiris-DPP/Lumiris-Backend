package com.minoh.lumiris_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public void sendRegistrationPending(String to, String name) {
        send(to, "Votre inscription est en cours de validation",
                "Bonjour " + name + ",\n\nVotre inscription sur Lumiris a bien été reçue. " +
                "Notre équipe va examiner votre dossier et vous notifiera dès que possible.\n\nÀ bientôt,\nL'équipe Lumiris");
    }

    public void sendVerified(String to, String name) {
        send(to, "Bienvenue sur le réseau Lumiris !",
                "Bonjour " + name + ",\n\nVotre compte artisan a été validé. " +
                "Vous pouvez désormais accéder à toutes les fonctionnalités de la plateforme.\n\nBienvenue !\nL'équipe Lumiris");
    }

    public void sendRejected(String to, String name, String reason) {
        String body = "Bonjour " + name + ",\n\nNous ne sommes pas en mesure de valider votre inscription sur Lumiris.";
        if (reason != null && !reason.isBlank()) {
            body += "\n\nMotif : " + reason;
        }
        body += "\n\nPour toute question, contactez-nous.\nL'équipe Lumiris";
        send(to, "Votre inscription Lumiris n'a pas pu être validée", body);
    }

    public void sendNotification(String to, String title, String body) {
        send(to, title, body + "\n\nRetrouvez le détail dans votre espace Lumiris.\nL'équipe Lumiris");
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
