package com.minoh.lumiris_backend.marketplace.decision.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.marketplace.decision.dto.out.DecisionLogResponse;
import com.minoh.lumiris_backend.marketplace.decision.service.MarketplaceDecisionLogService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Expose le log de décision d'un tri suggest/search pour un audit indépendant.
// Authentifié : la piste d'audit n'est pas destinée au grand public.
@RestController
@RequestMapping("/api/marketplace/decision-logs")
@RequiredArgsConstructor
public class MarketplaceDecisionLogController {

    private final MarketplaceDecisionLogService decisionLogService;

    // Un log de décision, pour l'audit interne (ADMIN).
    @GetMapping("/{id}")
    ResponseEntity<DecisionLogResponse> get(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(decisionLogService.getDecisionLog(email, id));
    }
}
