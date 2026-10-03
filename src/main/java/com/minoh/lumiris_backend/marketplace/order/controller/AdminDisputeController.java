package com.minoh.lumiris_backend.marketplace.order.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.marketplace.order.dto.in.DisputeResolutionRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.marketplace.order.service.DisputeService;
import com.minoh.lumiris_backend.marketplace.order.service.OrderLifecycleService;
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

/** Délègue les décisions de la plateforme sur les litiges. */
@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeService disputeService;
    private final OrderLifecycleService lifecycleService;

    /** Délègue l’action listOpen au service de commandes. */
    @GetMapping
    ResponseEntity<List<SellerOrderResponse>> listOpen() {
        return ResponseEntity.ok(disputeService.listOpen());
    }

    /** Ajoute un message au fil accessible aux parties et à la plateforme. */
    @PostMapping("/{orderId}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID orderId,
                                     @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, orderId, request);
        return ResponseEntity.noContent().build();
    }

    /** Délègue l’action resolve au service de commandes. */
    @PostMapping("/{orderId}/resolve")
    ResponseEntity<Void> resolve(@PathVariable UUID orderId,
                                 @Valid @RequestBody DisputeResolutionRequest request,
                                 @CurrentUserEmail String email) {
        lifecycleService.resolveDispute(email, orderId, request);
        return ResponseEntity.noContent().build();
    }
}
