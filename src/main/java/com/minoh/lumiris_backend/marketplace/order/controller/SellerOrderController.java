package com.minoh.lumiris_backend.marketplace.order.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.marketplace.order.dto.in.OrderMessageRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.RefundRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ReturnDecisionRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ShipOrderRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.marketplace.order.service.OrderLifecycleService;
import com.minoh.lumiris_backend.marketplace.order.service.SellerOrderService;
import com.minoh.lumiris_backend.marketplace.shipping.dto.out.ShippingLabelResponse;
import com.minoh.lumiris_backend.marketplace.shipping.service.ShippingLabelService;
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

@RestController
@RequestMapping("/api/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final SellerOrderService sellerOrderService;
    private final OrderLifecycleService lifecycleService;
    private final ShippingLabelService shippingLabelService;

    @GetMapping
    ResponseEntity<List<SellerOrderResponse>> orders(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerOrderService.list(email));
    }

    @GetMapping("/shipping")
    ResponseEntity<ShippingLabelResponse.Availability> shipping(@CurrentUserEmail String email) {
        return ResponseEntity.ok(shippingLabelService.availability(email));
    }

    @GetMapping("/{id}")
    ResponseEntity<SellerOrderResponse> order(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerOrderService.get(email, id));
    }

    @PostMapping("/{id}/ship")
    ResponseEntity<Void> ship(@PathVariable UUID id, @Valid @RequestBody ShipOrderRequest request,
                              @CurrentUserEmail String email) {
        lifecycleService.ship(email, id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/label")
    ResponseEntity<ShippingLabelResponse> generateLabel(@PathVariable UUID id,
                                                        @CurrentUserEmail String email) {
        return ResponseEntity.ok(shippingLabelService.generate(email, id));
    }

    @PostMapping("/{id}/return/decision")
    ResponseEntity<Void> decideReturn(@PathVariable UUID id, @Valid @RequestBody ReturnDecisionRequest request,
                                      @CurrentUserEmail String email) {
        lifecycleService.decideReturn(email, id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/return/received")
    ResponseEntity<Void> markReturnReceived(@PathVariable UUID id, @CurrentUserEmail String email) {
        lifecycleService.markReturnReceived(email, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody(required = false) OrderMessageRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.cancel(email, id, request != null ? request.reason() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refund")
    ResponseEntity<Void> refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.refund(email, id, request);
        return ResponseEntity.noContent().build();
    }
}
