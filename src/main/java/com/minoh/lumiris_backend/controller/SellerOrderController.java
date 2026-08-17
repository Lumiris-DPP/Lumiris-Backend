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

// LUMIRIS-24 · Tableau de bord des commandes vendeur (ATELIER) : à expédier, expédiées, retours,
// litiges. Chaque action est une transition contrôlée par OrderLifecycleService.
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

    // État de l'intégration transporteur, lu AVANT d'afficher le bouton d'impression : sans lui,
    // l'atelier découvrirait qu'il lui manque une adresse d'enlèvement au moment d'imprimer.
    @GetMapping("/shipping")
    ResponseEntity<ShippingLabelResponse.Availability> shipping(@CurrentUserEmail String email) {
        return ResponseEntity.ok(shippingLabelService.availability(email));
    }

    @GetMapping("/{id}")
    ResponseEntity<SellerOrderResponse> order(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerOrderService.get(email, id));
    }

    // Saisie du suivi + marquage de l'expédition (une seule action : un colis sans suivi n'est pas
    // suivable par l'acheteur, ce que le ticket exige).
    @PostMapping("/{id}/ship")
    ResponseEntity<Void> ship(@PathVariable UUID id, @Valid @RequestBody ShipOrderRequest request,
                              @CurrentUserEmail String email) {
        lifecycleService.ship(email, id, request);
        return ResponseEntity.noContent().build();
    }

    // Étiquette en un clic : bordereau fabriqué depuis l'adresse déjà stockée sur la commande,
    // suivi rempli automatiquement, commande passée en expédiée sans aucune saisie. La saisie
    // manuelle ci-dessus reste le chemin d'une remise en main propre ou d'un transporteur hors
    // agrégateur.
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

    // Fil de conversation avec l'acheteur (mêmes messages que côté VISION).
    @PostMapping("/{id}/messages")
    ResponseEntity<Void> postMessage(@PathVariable UUID id, @Valid @RequestBody OrderMessageRequest request,
                                     @CurrentUserEmail String email) {
        lifecycleService.postMessage(email, id, request);
        return ResponseEntity.noContent().build();
    }

    // Annulation avant expédition (rupture, pièce abîmée) : remboursement intégral de l'acheteur.
    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody(required = false) OrderMessageRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.cancel(email, id, request != null ? request.reason() : null);
        return ResponseEntity.noContent().build();
    }

    // Remboursement partiel ou total (montant absent ⇒ solde intégral).
    @PostMapping("/{id}/refund")
    ResponseEntity<Void> refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request,
                                @CurrentUserEmail String email) {
        lifecycleService.refund(email, id, request);
        return ResponseEntity.noContent().build();
    }
}
