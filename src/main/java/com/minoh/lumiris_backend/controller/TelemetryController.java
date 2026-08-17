package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.WebVitalRequest;
import com.minoh.lumiris_backend.service.WebVitalsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Ingestion anonyme des Web Vitals, émise par les quatre surfaces via sendBeacon (donc sans
// en-tête d'authentification possible). Aucune donnée personnelle : métrique, note, surface.
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final WebVitalsService webVitalsService;

    @PostMapping("/web-vitals")
    ResponseEntity<Void> webVital(@Valid @RequestBody WebVitalRequest request) {
        webVitalsService.record(request);
        return ResponseEntity.accepted().build();
    }
}
