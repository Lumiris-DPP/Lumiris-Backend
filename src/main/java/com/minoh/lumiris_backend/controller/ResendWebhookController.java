package com.minoh.lumiris_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.entity.EmailSuppression;
import com.minoh.lumiris_backend.service.RepairerProspectingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook Resend : sur un bounce dur ou une plainte, l'adresse rejoint la liste de suppression
 * (plus aucun e-mail de prospection). Sans ça, l'outbox retente indéfiniment une adresse morte.
 *
 * ponytail: pas de vérification de signature Svix. Le seul effet d'un appel falsifié est de
 * sur-supprimer une adresse (on cesse de lui écrire) — sans fuite ni envoi indésirable. Ajouter
 * la vérif HMAC (svix-id.svix-timestamp.body) si l'endpoint est abusé.
 */
@Slf4j
@RestController
@RequestMapping("/api/resend")
@RequiredArgsConstructor
public class ResendWebhookController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RepairerProspectingService prospectingService;

    @PostMapping("/webhook")
    ResponseEntity<String> webhook(@RequestBody String payload) {
        JsonNode root;
        try {
            root = MAPPER.readTree(payload);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("invalid payload");
        }

        String type = root.path("type").asText("");
        EmailSuppression.Reason reason = switch (type) {
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
