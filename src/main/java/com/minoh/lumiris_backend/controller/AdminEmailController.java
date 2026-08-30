package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.out.EmailOutboxResponse;
import com.minoh.lumiris_backend.entity.EmailOutboxStatus;
import com.minoh.lumiris_backend.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Logs d'envoi email consultables côté admin — role ADMIN imposé par SecurityConfig sur /api/admin/**.
@RestController
@RequestMapping("/api/admin/emails")
@RequiredArgsConstructor
public class AdminEmailController {

    private final MailService mailService;

    // Log d'envoi complet, tous statuts (PENDING/SENT/DEAD). status et recipientEmail filtrent en
    // option — la vue DLQ ci-dessous équivaut à ?status=DEAD, gardée telle quelle en plus pour la
    // compatibilité avec ce qui l'utilise déjà.
    @GetMapping
    ResponseEntity<List<EmailOutboxResponse>> list(@RequestParam(required = false) EmailOutboxStatus status,
                                                    @RequestParam(required = false) String recipientEmail,
                                                    @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(mailService.list(status, recipientEmail, page));
    }

    // Lignes email_outbox passées en DLQ (status DEAD) après épuisement des tentatives de retry.
    @GetMapping("/dead")
    ResponseEntity<List<EmailOutboxResponse>> listDead(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(mailService.listDead(page));
    }

    // Repasse une ligne DEAD en PENDING (tentatives à zéro) ; EmailOutboxDispatcher la reprend au
    // prochain passage. 409 si la ligne n'est pas en DLQ.
    @PostMapping("/{id}/retry")
    ResponseEntity<EmailOutboxResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(mailService.retryDead(id));
    }
}
