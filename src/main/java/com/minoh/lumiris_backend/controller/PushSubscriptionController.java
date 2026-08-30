package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.PushSubscriptionRequest;
import com.minoh.lumiris_backend.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Abonnements Web Push de l'utilisateur courant, et clé publique VAPID nécessaire côté navigateur
// pour PushManager.subscribe().
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService subscriptionService;

    @GetMapping("/vapid-public-key")
    ResponseEntity<Map<String, String>> vapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", subscriptionService.vapidPublicKey()));
    }

    @PostMapping("/subscriptions")
    ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request,
                                   @CurrentUserEmail String email) {
        subscriptionService.subscribe(email, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    ResponseEntity<Void> unsubscribe(@RequestParam String endpoint, @CurrentUserEmail String email) {
        subscriptionService.unsubscribe(email, endpoint);
        return ResponseEntity.noContent().build();
    }
}
