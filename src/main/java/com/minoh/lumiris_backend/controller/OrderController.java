package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.dto.in.ReturnRequest;
import com.minoh.lumiris_backend.dto.out.OrderDetailResponse;
import com.minoh.lumiris_backend.dto.out.OrderGroupResponse;
import com.minoh.lumiris_backend.dto.out.OrderResponse;
import com.minoh.lumiris_backend.service.BuyerOrderService;
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

// LUMIRIS-24 · Commandes côté acheteur (VISION) : suivi, confirmation de réception, demande de
// retour, ouverture et suivi de litige.
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final BuyerOrderService buyerOrderService;
    private final OrderLifecycleService lifecycleService;

    @GetMapping
    ResponseEntity<List<OrderResponse>> orders(@CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrders(email));
    }

    // Suivi d'une commande : état courant, adresse, suivi transporteur et timeline des transitions.
    @GetMapping("/{id}")
    ResponseEntity<OrderDetailResponse> order(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrder(email, id));
    }

    // Groupe de commande par PaymentIntent (confirmation) — toutes les lignes + montant exact débité.
    @GetMapping("/group/{paymentIntentId}")
    ResponseEntity<OrderGroupResponse> orderGroup(@PathVariable String paymentIntentId,
                                                  @CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrderGroup(email, paymentIntentId));
    }

    // « J'ai bien reçu ma commande » : clôt l'attente et libère les fonds au vendeur sans attendre
    // l'échéance automatique.
    @PostMapping("/{id}/received")
    ResponseEntity<Void> confirmDelivery(@PathVariable UUID id, @CurrentUserEmail String email) {
        lifecycleService.confirmDelivery(email, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/return")
    ResponseEntity<Void> requestReturn(@PathVariable UUID id, @Valid @RequestBody ReturnRequest request,
                                       @CurrentUserEmail String email) {
        lifecycleService.requestReturn(email, id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dispute")
    ResponseEntity<Void> openDispute(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.openDispute(email, id, request);
        return ResponseEntity.noContent().build();
    }

    // Fil de conversation avec l'atelier, ouvert à tout moment : poser une question ne doit pas
    // obliger à ouvrir un litige.
    @PostMapping("/{id}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, id, request);
        return ResponseEntity.noContent().build();
    }

    // Annulation avant expédition : remboursement intégral, la pièce retourne au catalogue.
    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody(required = false) OrderMessageRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.cancel(email, id, request != null ? request.reason() : null);
        return ResponseEntity.noContent().build();
    }
}
