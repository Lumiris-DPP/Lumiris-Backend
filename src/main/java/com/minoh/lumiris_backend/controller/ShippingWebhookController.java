package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.service.shipping.CarrierTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Agrégateur → serveur : événements du transporteur. Non authentifié, protégé par la vérification
// HMAC de la signature — d'où le corps BRUT (@RequestBody String), qui ne doit pas être parsé avant.
// Sans secret configuré, l'adaptateur rejette : cet endpoint fait avancer des commandes et libère
// des fonds, il ne traite jamais une charge utile non signée.
@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingWebhookController {

    private final CarrierTrackingService trackingService;

    @PostMapping("/webhook")
    ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(name = "Sendcloud-Signature", required = false) String signature
    ) {
        trackingService.handle(payload, signature);
        return ResponseEntity.ok("ok");
    }
}
