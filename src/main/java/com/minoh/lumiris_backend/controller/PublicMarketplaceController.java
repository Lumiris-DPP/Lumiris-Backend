package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.dto.in.SuggestRequest;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.dto.out.PaymentOptionsResponse;
import com.minoh.lumiris_backend.dto.out.SearchResponse;
import com.minoh.lumiris_backend.dto.out.SuggestionResponse;
import com.minoh.lumiris_backend.service.PublicCatalogService;
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

// Expose le catalogue accessible aux visiteurs.
@RestController
@RequestMapping("/public/marketplace")
@RequiredArgsConstructor
public class PublicMarketplaceController {

    private final PublicCatalogService publicCatalogService;
    private final MarketplaceProperties marketplaceProperties;

    // Présente les options de paiement du catalogue.
    @GetMapping("/payment-options")
    ResponseEntity<PaymentOptionsResponse> paymentOptions() {
        return ResponseEntity.ok(PaymentOptionsResponse.from(marketplaceProperties));
    }

    // Recherche et classe les pièces selon les critères reçus.
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

    // Propose des pièces selon le score et la catégorie.
    @PostMapping("/suggest")
    ResponseEntity<SuggestionResponse> suggest(@Valid @RequestBody SuggestRequest request) {
        return ResponseEntity.ok(publicCatalogService.suggest(request));
    }

    // Retrouve les pièces du catalogue désignées par leurs références.
    @GetMapping("/products")
    ResponseEntity<List<MarketplaceItemResponse>> productsByIds(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(publicCatalogService.getPublishedByIds(ids));
    }

    // Retrouve une pièce disponible dans le catalogue public.
    @GetMapping("/products/{id}")
    ResponseEntity<MarketplaceItemResponse> product(@PathVariable UUID id) {
        return ResponseEntity.ok(publicCatalogService.getPublished(id));
    }

    // Retrouve la pièce vendue depuis le passeport demandé.
    @GetMapping("/products/by-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> productByDpp(@PathVariable UUID dppFormId) {
        return ResponseEntity.ok(publicCatalogService.getPublishedByDpp(dppFormId));
    }

    // Compte une consultation de la pièce publiée.
    @PostMapping("/products/{id}/view")
    ResponseEntity<Void> trackView(@PathVariable UUID id) {
        publicCatalogService.trackView(id);
        return ResponseEntity.accepted().build();
    }
}
