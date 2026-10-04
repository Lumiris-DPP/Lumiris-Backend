package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.DisputeResolutionRequest;
import com.minoh.lumiris_backend.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.service.DisputeService;
import com.minoh.lumiris_backend.service.OrderLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Expose les litiges et leur résolution à la plateforme.
@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeService disputeService;
    private final OrderLifecycleService lifecycleService;

    // Liste les litiges ouverts sur les commandes.
    @GetMapping
    ResponseEntity<List<SellerOrderResponse>> listOpen() {
        return ResponseEntity.ok(disputeService.listOpen());
    }

    // Ajoute un message à l'historique de la commande.
    @PostMapping("/{orderId}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID orderId,
                                     @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, orderId, request);
        return ResponseEntity.noContent().build();
    }

    // Applique la décision de la plateforme au litige.
    @PostMapping("/{orderId}/resolve")
    ResponseEntity<Void> resolve(@PathVariable UUID orderId,
                                 @Valid @RequestBody DisputeResolutionRequest request,
                                 @CurrentUserEmail String email) {
        lifecycleService.resolveDispute(email, orderId, request);
        return ResponseEntity.noContent().build();
    }
}
