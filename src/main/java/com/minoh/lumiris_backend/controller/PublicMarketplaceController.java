package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.SuggestRequest;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.dto.out.SearchResponse;
import com.minoh.lumiris_backend.dto.out.SuggestionResponse;
import com.minoh.lumiris_backend.service.MarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Surface publique du marketplace (accessible sans auth — l'app VISION mobile n'a pas
// de session backend). Recherche catalogue filtrée + suggestions sur DPP scanné.
@RestController
@RequestMapping("/public/marketplace")
@RequiredArgsConstructor
public class PublicMarketplaceController {

    private final MarketplaceService marketplaceService;

    // Filtres combinables (catégorie, matière, origine) + tri neutre par défaut.
    // `personalize` = catégories d'affinité de l'utilisateur connecté (reco perso).
    @GetMapping("/search")
    ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) List<String> personalize) {
        return ResponseEntity.ok(marketplaceService.search(category, material, origin, sort, personalize));
    }

    @PostMapping("/suggest")
    ResponseEntity<SuggestionResponse> suggest(@Valid @RequestBody SuggestRequest request) {
        return ResponseEntity.ok(marketplaceService.suggest(request));
    }

    // Fiche produit publiée unitaire (deep-link VISION) — 404 si non publiée / vendeur non encaissable.
    @GetMapping("/products/{id}")
    ResponseEntity<MarketplaceItemResponse> product(@PathVariable UUID id) {
        return ResponseEntity.ok(marketplaceService.getPublished(id));
    }

    // Pont scan → achat : produit achetable lié à un passeport scanné (unifie les 2 modèles d'achat).
    @GetMapping("/products/by-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> productByDpp(@PathVariable UUID dppFormId) {
        return ResponseEntity.ok(marketplaceService.getPublishedByDpp(dppFormId));
    }

    // Vue d'une fiche produit (VISION) — incrément fire-and-forget du compteur de vues (stats vendeur).
    @PostMapping("/products/{id}/view")
    ResponseEntity<Void> trackView(@PathVariable UUID id) {
        marketplaceService.trackView(id);
        return ResponseEntity.accepted().build();
    }
}
