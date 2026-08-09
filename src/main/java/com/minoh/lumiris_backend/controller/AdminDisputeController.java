package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.DisputeResolutionRequest;
import com.minoh.lumiris_backend.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.service.DisputeService;
import com.minoh.lumiris_backend.service.OrderLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// LUMIRIS-24 · Arbitrage des litiges par la plateforme. Le vendeur ne peut pas clôturer un litige
// qui le vise — seul un ADMIN tranche (rôle imposé par SecurityConfig sur /api/admin/**).
@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeService disputeService;
    private final OrderLifecycleService lifecycleService;

    @GetMapping
    ResponseEntity<List<SellerOrderResponse>> listOpen() {
        return ResponseEntity.ok(disputeService.listOpen());
    }

    // L'arbitre écrit dans le fil de la commande — les deux parties le lisent au même endroit que
    // le reste de la conversation.
    @PostMapping("/{orderId}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID orderId,
                                     @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, orderId, request);
        return ResponseEntity.noContent().build();
    }

    // `refundCents` renseigné ⇒ tranché en faveur de l'acheteur ; absent ⇒ clos sans remboursement.
    @PostMapping("/{orderId}/resolve")
    ResponseEntity<Void> resolve(@PathVariable UUID orderId,
                                 @Valid @RequestBody DisputeResolutionRequest request,
                                 @CurrentUserEmail String email) {
        lifecycleService.resolveDispute(email, orderId, request);
        return ResponseEntity.noContent().build();
    }
}
