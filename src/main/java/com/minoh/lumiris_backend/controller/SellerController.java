package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.CheckoutResponse;
import com.minoh.lumiris_backend.dto.out.SellerEarningsResponse;
import com.minoh.lumiris_backend.dto.out.SellerSaleResponse;
import com.minoh.lumiris_backend.dto.out.SellerStatsResponse;
import com.minoh.lumiris_backend.dto.out.SellerStatusResponse;
import com.minoh.lumiris_backend.service.stripe.SellerConnectService;
import com.minoh.lumiris_backend.service.stripe.SellerPayoutService;
import com.minoh.lumiris_backend.service.stripe.SellerStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// LUMIRIS-22 · Onboarding vendeur Stripe Connect (Express) + tableau de bord natif (ventes, revenus,
// virements) — réservé aux artisans. Aucun renvoi vers le tableau de bord hébergé Stripe : tout est natif.
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerConnectService sellerConnectService;
    private final SellerStatsService sellerStatsService;
    private final SellerPayoutService sellerPayoutService;

    // Crée/reprend le compte Express et renvoie l'URL d'onboarding hébergée (redirection Stripe).
    @PostMapping("/onboarding")
    ResponseEntity<CheckoutResponse> onboarding(@CurrentUserEmail String email) {
        return ResponseEntity.ok(new CheckoutResponse(sellerConnectService.startOnboarding(email)));
    }

    @GetMapping("/status")
    ResponseEntity<SellerStatusResponse> status(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerConnectService.getStatus(email));
    }

    // Tableau de bord vendeur : ventes, CA net, garde-robe, vues.
    @GetMapping("/stats")
    ResponseEntity<SellerStatsResponse> stats(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getStats(email));
    }

    // Historique des ventes de l'atelier.
    @GetMapping("/sales")
    ResponseEntity<List<SellerSaleResponse>> sales(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getSales(email));
    }

    // Trésorerie escrow : nets retenus (en attente d'expédition) vs déjà reversés.
    @GetMapping("/earnings")
    ResponseEntity<SellerEarningsResponse> earnings(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getEarnings(email));
    }

    // Marque une vente comme expédiée → libère les fonds retenus (Transfer vers le compte du vendeur).
    @PostMapping("/sales/{orderId}/ship")
    ResponseEntity<Void> ship(@PathVariable UUID orderId, @CurrentUserEmail String email) {
        sellerPayoutService.releaseOrder(email, orderId);
        return ResponseEntity.noContent().build();
    }
}
