package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.CheckoutResponse;
import com.minoh.lumiris_backend.dto.out.SellerEarningsResponse;
import com.minoh.lumiris_backend.dto.out.SellerPayoutScheduleResponse;
import com.minoh.lumiris_backend.dto.out.SellerSaleResponse;
import com.minoh.lumiris_backend.dto.out.SellerStatsResponse;
import com.minoh.lumiris_backend.dto.out.SellerStatusResponse;
import com.minoh.lumiris_backend.service.stripe.SellerConnectService;
import com.minoh.lumiris_backend.service.stripe.SellerStatsService;
import com.minoh.lumiris_backend.dto.in.ShipFromAddressRequest;
import com.minoh.lumiris_backend.dto.out.ShipFromAddressResponse;
import com.minoh.lumiris_backend.service.shipping.ShipFromAddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Expose le compte de paiement et le bilan vendeur.
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerConnectService sellerConnectService;
    private final SellerStatsService sellerStatsService;
    private final ShipFromAddressService shipFromAddressService;

    // Prépare le lien d'activation du compte vendeur.
    @PostMapping("/onboarding")
    ResponseEntity<CheckoutResponse> onboarding(@CurrentUserEmail String email) {
        return ResponseEntity.ok(new CheckoutResponse(sellerConnectService.startOnboarding(email)));
    }

    // Consulte l'activation du compte de paiement vendeur.
    @GetMapping("/status")
    ResponseEntity<SellerStatusResponse> status(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerConnectService.getStatus(email));
    }

    // Présente le bilan des annonces et des ventes vendeur.
    @GetMapping("/stats")
    ResponseEntity<SellerStatsResponse> stats(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getStats(email));
    }

    // Liste les ventes de l'atelier connecté.
    @GetMapping("/sales")
    ResponseEntity<List<SellerSaleResponse>> sales(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getSales(email));
    }

    // Présente les montants retenus et versés à l'atelier.
    @GetMapping("/earnings")
    ResponseEntity<SellerEarningsResponse> earnings(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getEarnings(email));
    }

    // Présente les versements attendus pour les commandes vendeur.
    @GetMapping("/payouts")
    ResponseEntity<SellerPayoutScheduleResponse> payouts(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getPayoutSchedule(email));
    }

    // Consulte l'adresse d'expédition de l'atelier connecté.
    @GetMapping("/shipping-address")
    ResponseEntity<ShipFromAddressResponse> shipFromAddress(@CurrentUserEmail String email) {
        return ResponseEntity.ok(shipFromAddressService.get(email));
    }

    // Enregistre l'adresse d'expédition de l'atelier connecté.
    @PutMapping("/shipping-address")
    ResponseEntity<ShipFromAddressResponse> updateShipFromAddress(
            @Valid @RequestBody ShipFromAddressRequest request,
            @CurrentUserEmail String email) {
        return ResponseEntity.ok(shipFromAddressService.update(email, request));
    }
}
