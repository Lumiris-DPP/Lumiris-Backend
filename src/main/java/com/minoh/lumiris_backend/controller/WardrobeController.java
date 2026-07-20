package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.out.OrderResponse;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.service.stripe.DirectSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// LUMIRIS-22 · Garde-Robe de l'acheteur (pièces achetées en direct) + ses commandes.
@RestController
@RequiredArgsConstructor
public class WardrobeController {

    private final DirectSaleService directSaleService;

    // Panier → PaymentIntent Connect (paiement embarqué via Payment Element, sans redirection).
    @PostMapping("/api/marketplace/checkout/intent")
    ResponseEntity<PaymentIntentResponse> checkoutIntent(@Valid @RequestBody CartIntentRequest request,
                                                         @CurrentUserEmail String email) {
        return ResponseEntity.ok(directSaleService.createCartPaymentIntent(email, request.items()));
    }

    @GetMapping("/api/wardrobe")
    ResponseEntity<List<WardrobeItemResponse>> wardrobe(@CurrentUserEmail String email) {
        return ResponseEntity.ok(directSaleService.getWardrobe(email));
    }

    @GetMapping("/api/orders")
    ResponseEntity<List<OrderResponse>> orders(@CurrentUserEmail String email) {
        return ResponseEntity.ok(directSaleService.getMyOrders(email));
    }
}
