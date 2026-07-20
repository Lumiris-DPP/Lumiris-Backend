package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.CheckoutResponse;
import com.minoh.lumiris_backend.dto.out.SellerStatsResponse;
import com.minoh.lumiris_backend.dto.out.SellerStatusResponse;
import com.minoh.lumiris_backend.service.stripe.SellerConnectService;
import com.minoh.lumiris_backend.service.stripe.SellerStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// LUMIRIS-22 · Onboarding vendeur Stripe Connect (Express) + tableau de bord — réservé aux artisans.
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerConnectService sellerConnectService;
    private final SellerStatsService sellerStatsService;

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

    // Lien vers le tableau de bord Stripe Express (solde + virements encaissés).
    @GetMapping("/dashboard-link")
    ResponseEntity<CheckoutResponse> dashboardLink(@CurrentUserEmail String email) {
        return ResponseEntity.ok(new CheckoutResponse(sellerConnectService.createDashboardLink(email)));
    }
}
