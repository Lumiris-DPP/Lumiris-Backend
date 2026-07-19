package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.AffiliateTrackRequest;
import com.minoh.lumiris_backend.service.AffiliateTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Tracking des clics d'affiliation externe. Public (appelé depuis l'app VISION),
// fire-and-forget : renvoie 202 sans corps.
@RestController
@RequestMapping("/public/track")
@RequiredArgsConstructor
public class AffiliateTrackController {

    private final AffiliateTrackingService affiliateTrackingService;

    @PostMapping("/affiliate")
    ResponseEntity<Void> affiliate(@Valid @RequestBody AffiliateTrackRequest request) {
        affiliateTrackingService.track(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
