package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.NotificationPreferenceUpdateRequest;
import com.minoh.lumiris_backend.dto.out.NotificationPreferenceResponse;
import com.minoh.lumiris_backend.entity.NotificationCategory;
import com.minoh.lumiris_backend.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Préférences de désabonnement de l'utilisateur courant, par catégorie — /me/settings côté front.
@RestController
@RequestMapping("/api/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    ResponseEntity<List<NotificationPreferenceResponse>> list(@CurrentUserEmail String email) {
        return ResponseEntity.ok(preferenceService.list(email));
    }

    @PutMapping("/{category}")
    ResponseEntity<NotificationPreferenceResponse> update(@PathVariable NotificationCategory category,
                                                           @Valid @RequestBody NotificationPreferenceUpdateRequest request,
                                                           @CurrentUserEmail String email) {
        return ResponseEntity.ok(preferenceService.update(email, category, request));
    }
}
