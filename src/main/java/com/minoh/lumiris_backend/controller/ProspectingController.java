package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.service.RepairerProspectingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Désinscription one-click de la prospection retoucheurs. Public, fonctionne sur un simple GET
// (RFC 8058) et renvoie une page de confirmation minimale.
@RestController
@RequestMapping("/v1/prospecting")
@RequiredArgsConstructor
public class ProspectingController {

    private final RepairerProspectingService prospectingService;

    @GetMapping(value = "/unsubscribe/{id}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> unsubscribe(@PathVariable UUID id) {
        prospectingService.unsubscribe(id);
        return ResponseEntity.ok("""
                <!doctype html><html lang="fr"><head><meta charset="utf-8">
                <title>Désinscription confirmée</title></head>
                <body style="font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem;color:#1f1b16;">
                <h1 style="font-size:1.25rem;">C'est fait</h1>
                <p>Vous ne recevrez plus d'e-mail de prospection de la part de Lumiris.</p>
                </body></html>""");
    }
}
