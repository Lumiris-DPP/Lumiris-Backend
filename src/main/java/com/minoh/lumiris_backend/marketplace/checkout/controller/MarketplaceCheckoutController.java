package com.minoh.lumiris_backend.marketplace.checkout.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.marketplace.checkout.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.marketplace.checkout.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.marketplace.checkout.service.DirectSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Expose la préparation du paiement du panier de l’acheteur authentifié. */
@RestController
@RequiredArgsConstructor
public class MarketplaceCheckoutController {

    private final DirectSaleService directSaleService;

    /** Prépare une intention payable avec les réservations du panier. */
    @PostMapping("/api/marketplace/checkout/intent")
    ResponseEntity<PaymentIntentResponse> checkoutIntent(@Valid @RequestBody CartIntentRequest request,
                                                      @CurrentUserEmail String email) {
        return ResponseEntity.ok(directSaleService.createCartPaymentIntent(email, request));
    }
}
