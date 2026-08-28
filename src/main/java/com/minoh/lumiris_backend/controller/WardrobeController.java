package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.in.WardrobeSyncRequest;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.service.BuyerOrderService;
import com.minoh.lumiris_backend.service.WardrobeSyncService;
import com.minoh.lumiris_backend.service.stripe.DirectSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// LUMIRIS-22 · Garde-Robe de l'acheteur (pièces achetées en direct) et entrée du paiement.
// Le suivi des commandes elles-mêmes vit dans OrderController.
@RestController
@RequiredArgsConstructor
public class WardrobeController {

    private final DirectSaleService directSaleService;
    private final BuyerOrderService buyerOrderService;
    private final WardrobeSyncService wardrobeSyncService;

    // Panier → PaymentIntent (paiement embarqué via Payment Element, sans redirection). Le panier
    // peut couvrir plusieurs ateliers : un colis et un reversement par atelier.
    @PostMapping("/api/marketplace/checkout/intent")
    ResponseEntity<PaymentIntentResponse> checkoutIntent(@Valid @RequestBody CartIntentRequest request,
                                                         @CurrentUserEmail String email) {
        return ResponseEntity.ok(directSaleService.createCartPaymentIntent(email, request));
    }

    @GetMapping("/api/wardrobe")
    ResponseEntity<List<WardrobeItemResponse>> wardrobe(@CurrentUserEmail String email) {
        return ResponseEntity.ok(buyerOrderService.getWardrobe(email));
    }

    @PostMapping("/api/wardrobe/sync")
    ResponseEntity<List<WardrobeItemResponse>> syncWardrobe(@Valid @RequestBody WardrobeSyncRequest request,
                                                            @CurrentUserEmail String email) {
        return ResponseEntity.ok(wardrobeSyncService.sync(email, request));
    }
}
