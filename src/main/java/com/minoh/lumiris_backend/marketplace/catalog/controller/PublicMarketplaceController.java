package com.minoh.lumiris_backend.marketplace.catalog.controller;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.SuggestRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.PaymentOptionsResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.SearchResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.SuggestionResponse;
import com.minoh.lumiris_backend.marketplace.catalog.service.PublicCatalogService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Surface publique du marketplace (accessible sans auth — l'app VISION mobile n'a pas
// de session backend). Recherche catalogue filtrée + suggestions sur DPP scanné.
@RestController
@RequestMapping("/public/marketplace")
@RequiredArgsConstructor
public class PublicMarketplaceController {

    private final PublicCatalogService publicCatalogService;
    private final MarketplaceProperties marketplaceProperties;

    // Facilités de paiement annonçables. Public et sans état : la fiche produit et le panier
    // doivent pouvoir dire « ou 3× 100 € » AVANT toute session, c'est-à-dire au moment où
    // l'acheteur hésite — pas au dernier écran du tunnel.
    @GetMapping("/payment-options")
    ResponseEntity<PaymentOptionsResponse> paymentOptions() {
        return ResponseEntity.ok(PaymentOptionsResponse.from(marketplaceProperties));
    }

    // Recherche plein texte (`q`) et filtres combinables (catégorie, matière, origine) + tri neutre
    // par défaut. `personalize` = catégories d'affinité de l'utilisateur connecté (reco perso).
    @GetMapping("/search")
    ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) List<String> personalize) {
        return ResponseEntity.ok(publicCatalogService.search(q, category, material, origin, sort, personalize));
    }

    // Jusqu'à trois alternatives de score au moins égal à celui d'un passeport scanné.
    @PostMapping("/suggest")
    ResponseEntity<SuggestionResponse> suggest(@Valid @RequestBody SuggestRequest request) {
        return ResponseEntity.ok(publicCatalogService.suggest(request));
    }

    // Fiches d'un panier en un appel (`?ids=a,b,c`). Les produits devenus indisponibles sont
    // simplement absents de la réponse : le panier en déduit ce qu'il doit retirer, et le dire.
    @GetMapping("/products")
    ResponseEntity<List<MarketplaceItemResponse>> productsByIds(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(publicCatalogService.getPublishedByIds(ids));
    }

    // Fiche produit publiée unitaire (deep-link VISION) — 404 si non publiée / vendeur non encaissable.
    @GetMapping("/products/{id}")
    ResponseEntity<MarketplaceItemResponse> product(@PathVariable UUID id) {
        return ResponseEntity.ok(publicCatalogService.getPublished(id));
    }

    // Pont scan → achat : produit achetable lié à un passeport scanné (unifie les 2 modèles d'achat).
    @GetMapping("/products/by-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> productByDpp(@PathVariable UUID dppFormId) {
        return ResponseEntity.ok(publicCatalogService.getPublishedByDpp(dppFormId));
    }

    // Vue d'une fiche produit (VISION) — incrément fire-and-forget du compteur de vues (stats vendeur).
    @PostMapping("/products/{id}/view")
    ResponseEntity<Void> trackView(@PathVariable UUID id) {
        publicCatalogService.trackView(id);
        return ResponseEntity.accepted().build();
    }
}
