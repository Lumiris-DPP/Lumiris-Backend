package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.service.RepairerProspectingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Base64;
import java.util.UUID;

// Liens tracés des e-mails de prospection retoucheurs : pixel d'ouverture, redirection de clic,
// désinscription one-click. Public, GET-safe.
@RestController
@RequestMapping("/v1/prospecting")
@RequiredArgsConstructor
public class ProspectingController {

    // GIF transparent 1×1.
    private static final byte[] PIXEL = Base64.getDecoder()
            .decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final RepairerProspectingService prospectingService;

    @GetMapping("/open/{id}")
    ResponseEntity<byte[]> open(@PathVariable UUID id) {
        prospectingService.recordOpen(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .cacheControl(CacheControl.noStore())
                .body(PIXEL);
    }

    @GetMapping("/click/{id}")
    ResponseEntity<Void> click(@PathVariable UUID id) {
        String target = prospectingService.recordClick(id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @GetMapping(value = "/unsubscribe/{id}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> unsubscribe(@PathVariable UUID id) {
        prospectingService.unsubscribe(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue())
                .body("""
                        <!doctype html><html lang="fr"><head><meta charset="utf-8">
                        <title>Désinscription confirmée</title></head>
                        <body style="font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem;color:#1f1b16;">
                        <h1 style="font-size:1.25rem;">C'est fait</h1>
                        <p>Vous ne recevrez plus d'e-mail de prospection de la part de Lumiris.</p>
                        </body></html>""");
    }
}
