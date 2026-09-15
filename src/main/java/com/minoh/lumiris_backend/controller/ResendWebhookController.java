package com.minoh.lumiris_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.entity.EmailSuppression;
import com.minoh.lumiris_backend.exception.WebhookSignatureException;
import com.minoh.lumiris_backend.service.RepairerProspectingService;
import com.minoh.lumiris_backend.service.SvixSignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook Resend : sur un bounce dur ou une plainte, l'adresse rejoint la liste de suppression
 * (plus aucun e-mail de prospection). Signature Svix vérifiée dès que {@code resend.webhook-secret}
 * est configuré ; sinon (dev) on accepte sans vérifier — un appel falsifié ne fait que
 * sur-supprimer une adresse, sans fuite ni envoi indésirable.
 */
@Slf4j
@RestController
@RequestMapping("/api/resend")
@RequiredArgsConstructor
public class ResendWebhookController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RepairerProspectingService prospectingService;

    @Value("${resend.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/webhook")
    ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTimestamp,
            @RequestHeader(value = "svix-signature", required = false) String svixSignature
    ) {
        if (webhookSecret != null && !webhookSecret.isBlank()
                && !SvixSignature.verify(webhookSecret, svixId, svixTimestamp, svixSignature, payload)) {
            throw new WebhookSignatureException("Signature de webhook Resend invalide.");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(payload);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("invalid payload");
        }

        EmailSuppression.Reason reason = switch (root.path("type").asText("")) {
            case "email.bounced" -> EmailSuppression.Reason.BOUNCE;
            case "email.complained" -> EmailSuppression.Reason.COMPLAINT;
            default -> null;
        };
        if (reason == null) {
            return ResponseEntity.ok("ignored");
        }

        for (JsonNode recipient : root.path("data").path("to")) {
            prospectingService.suppress(recipient.asText(null), reason);
        }
        return ResponseEntity.ok("ok");
    }
}
