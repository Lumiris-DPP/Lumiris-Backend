package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.NotificationResponse;
import com.minoh.lumiris_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Notifications in-app du destinataire courant — mêmes routes pour ATELIER et VISION, le contenu
// dépend du rôle des commandes qui l'ont déclenché.
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    ResponseEntity<List<NotificationResponse>> list(@CurrentUserEmail String email) {
        return ResponseEntity.ok(notificationService.list(email));
    }

    @GetMapping("/unread-count")
    ResponseEntity<Map<String, Long>> unreadCount(@CurrentUserEmail String email) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(email)));
    }

    @PostMapping("/{id}/read")
    ResponseEntity<Void> markRead(@PathVariable UUID id, @CurrentUserEmail String email) {
        notificationService.markRead(email, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    ResponseEntity<Void> markAllRead(@CurrentUserEmail String email) {
        notificationService.markAllRead(email);
        return ResponseEntity.noContent().build();
    }
}
