package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.dto.in.RefundRequest;
import com.minoh.lumiris_backend.dto.in.ReturnDecisionRequest;
import com.minoh.lumiris_backend.dto.in.ShipOrderRequest;
import com.minoh.lumiris_backend.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.dto.out.ShippingLabelResponse;
import com.minoh.lumiris_backend.service.OrderLifecycleService;
import com.minoh.lumiris_backend.service.SellerOrderService;
import com.minoh.lumiris_backend.service.shipping.ShippingLabelService;
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

/** Délègue les lectures et actions du vendeur aux services de commandes. */
@RestController
@RequestMapping("/api/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final SellerOrderService sellerOrderService;
    private final OrderLifecycleService lifecycleService;
    private final ShippingLabelService shippingLabelService;

    /** Délègue l’action orders au service de commandes. */
    @GetMapping
    ResponseEntity<List<SellerOrderResponse>> orders(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerOrderService.list(email));
    }

    /** Délègue l’action shipping au service de commandes. */
    @GetMapping("/shipping")
    ResponseEntity<ShippingLabelResponse.Availability> shipping(@CurrentUserEmail String email) {
        return ResponseEntity.ok(shippingLabelService.availability(email));
    }

    /** Délègue l’action order au service de commandes. */
    @GetMapping("/{id}")
    ResponseEntity<SellerOrderResponse> order(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerOrderService.get(email, id));
    }

    /** Expédie une commande payée appartenant au vendeur. */
    @PostMapping("/{id}/ship")
    ResponseEntity<Void> ship(@PathVariable UUID id, @Valid @RequestBody ShipOrderRequest request,
                              @CurrentUserEmail String email) {
        lifecycleService.ship(email, id, request);
        return ResponseEntity.noContent().build();
    }

    /** Délègue l’action generateLabel au service de commandes. */
    @PostMapping("/{id}/label")
    ResponseEntity<ShippingLabelResponse> generateLabel(@PathVariable UUID id,
                                                        @CurrentUserEmail String email) {
        return ResponseEntity.ok(shippingLabelService.generate(email, id));
    }

    /** Accepte ou refuse la demande de retour de l’acheteur. */
    @PostMapping("/{id}/return/decision")
    ResponseEntity<Void> decideReturn(@PathVariable UUID id, @Valid @RequestBody ReturnDecisionRequest request,
                                      @CurrentUserEmail String email) {
        lifecycleService.decideReturn(email, id, request);
        return ResponseEntity.noContent().build();
    }

    /** Enregistre la réception du retour accepté par l’atelier. */
    @PostMapping("/{id}/return/received")
    ResponseEntity<Void> markReturnReceived(@PathVariable UUID id, @CurrentUserEmail String email) {
        lifecycleService.markReturnReceived(email, id);
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

    /** Exécute une opération de remboursement avec une clé stable pour ses reprises. */
    @PostMapping("/{id}/refund")
    ResponseEntity<Void> refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.refund(email, id, request);
        return ResponseEntity.noContent().build();
    }
}
