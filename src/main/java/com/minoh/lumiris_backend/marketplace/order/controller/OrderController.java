package com.minoh.lumiris_backend.marketplace.order.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.marketplace.order.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ReturnRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.out.OrderDetailResponse;
import com.minoh.lumiris_backend.marketplace.order.dto.out.OrderGroupResponse;
import com.minoh.lumiris_backend.marketplace.order.dto.out.OrderResponse;
import com.minoh.lumiris_backend.marketplace.order.service.BuyerOrderService;
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

// Expose les commandes et les actions de l'acheteur.
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final BuyerOrderService buyerOrderService;
    private final OrderLifecycleService lifecycleService;

    // Liste les commandes de l'acheteur connecté.
    @GetMapping
    ResponseEntity<List<OrderResponse>> orders(@CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrders(email));
    }

    // Présente la commande demandée par son acheteur.
    @GetMapping("/{id}")
    ResponseEntity<OrderDetailResponse> order(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrder(email, id));
    }

    // Présente les commandes de l'acheteur issues du paiement.
    @GetMapping("/group/{paymentIntentId}")
    ResponseEntity<OrderGroupResponse> orderGroup(@PathVariable String paymentIntentId,
                                                  @CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrderGroup(email, paymentIntentId));
    }

    // Enregistre la livraison confirmée par l'acheteur.
    @PostMapping("/{id}/received")
    ResponseEntity<Void> confirmDelivery(@PathVariable UUID id, @CurrentUserEmail String email) {
        lifecycleService.confirmDelivery(email, id);
        return ResponseEntity.noContent().build();
    }

    // Enregistre la demande de retour de l'acheteur.
    @PostMapping("/{id}/return")
    ResponseEntity<Void> requestReturn(@PathVariable UUID id, @Valid @RequestBody ReturnRequest request,
                                       @CurrentUserEmail String email) {
        lifecycleService.requestReturn(email, id, request);
        return ResponseEntity.noContent().build();
    }

    // Ouvre un litige demandé par l'acheteur.
    @PostMapping("/{id}/dispute")
    ResponseEntity<Void> openDispute(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.openDispute(email, id, request);
        return ResponseEntity.noContent().build();
    }

    // Ajoute un message à l'historique de la commande.
    @PostMapping("/{id}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, id, request);
        return ResponseEntity.noContent().build();
    }

    // Annule la commande payée à la demande de l'acheteur.
    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody(required = false) OrderMessageRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.cancel(email, id, request != null ? request.reason() : null);
        return ResponseEntity.noContent().build();
    }
}
