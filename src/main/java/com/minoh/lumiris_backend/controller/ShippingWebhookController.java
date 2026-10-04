package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.service.shipping.CarrierTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Reçoit les changements de suivi du transporteur.
@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingWebhookController {

    private final CarrierTrackingService trackingService;

    // Transmet l'événement de suivi reçu au traitement transporteur.
    @PostMapping("/webhook")
    ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(name = "Sendcloud-Signature", required = false) String signature
    ) {
        trackingService.handle(payload, signature);
        return ResponseEntity.ok("ok");
    }
}
