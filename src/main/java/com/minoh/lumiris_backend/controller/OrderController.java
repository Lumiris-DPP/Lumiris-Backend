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

/** Délègue les lectures et actions de l’acheteur aux services de commandes. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final BuyerOrderService buyerOrderService;
    private final OrderLifecycleService lifecycleService;

    /** Délègue l’action orders au service de commandes. */
    @GetMapping
    ResponseEntity<List<OrderResponse>> orders(@CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrders(email));
    }

    /** Délègue l’action order au service de commandes. */
    @GetMapping("/{id}")
    ResponseEntity<OrderDetailResponse> order(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrder(email, id));
    }

    /** Délègue l’action orderGroup au service de commandes. */
    @GetMapping("/group/{paymentIntentId}")
    ResponseEntity<OrderGroupResponse> orderGroup(@PathVariable String paymentIntentId,
                                                  @CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getMyOrderGroup(email, paymentIntentId));
    }

    /** Confirme la réception d’une commande appartenant à l’acheteur. */
    @PostMapping("/{id}/received")
    ResponseEntity<Void> confirmDelivery(@PathVariable UUID id, @CurrentUserEmail String email) {
        lifecycleService.confirmDelivery(email, id);
        return ResponseEntity.noContent().build();
    }

    /** Ouvre un retour dans la fenêtre prévue pour cette commande. */
    @PostMapping("/{id}/return")
    ResponseEntity<Void> requestReturn(@PathVariable UUID id, @Valid @RequestBody ReturnRequest request,
                                       @CurrentUserEmail String email) {
        lifecycleService.requestReturn(email, id, request);
        return ResponseEntity.noContent().build();
    }

    /** Ouvre un litige sur une commande payée de l’acheteur. */
    @PostMapping("/{id}/dispute")
    ResponseEntity<Void> openDispute(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.openDispute(email, id, request);
        return ResponseEntity.noContent().build();
    }

    /** Ajoute un message au fil accessible aux parties et à la plateforme. */
    @PostMapping("/{id}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, id, request);
        return ResponseEntity.noContent().build();
    }

    /** Annule avant expédition avec remboursement intégral et remise en stock. */
    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody(required = false) OrderMessageRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.cancel(email, id, request != null ? request.reason() : null);
        return ResponseEntity.noContent().build();
    }
}
