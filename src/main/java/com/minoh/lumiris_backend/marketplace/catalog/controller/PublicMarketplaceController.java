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

@RestController
@RequestMapping("/public/marketplace")
@RequiredArgsConstructor
public class PublicMarketplaceController {

    private final PublicCatalogService publicCatalogService;
    private final MarketplaceProperties marketplaceProperties;

    @GetMapping("/payment-options")
    ResponseEntity<PaymentOptionsResponse> paymentOptions() {
        return ResponseEntity.ok(PaymentOptionsResponse.from(marketplaceProperties));
    }

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

    @PostMapping("/suggest")
    ResponseEntity<SuggestionResponse> suggest(@Valid @RequestBody SuggestRequest request) {
        return ResponseEntity.ok(publicCatalogService.suggest(request));
    }

    @GetMapping("/products")
    ResponseEntity<List<MarketplaceItemResponse>> productsByIds(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(publicCatalogService.getPublishedByIds(ids));
    }

    @GetMapping("/products/{id}")
    ResponseEntity<MarketplaceItemResponse> product(@PathVariable UUID id) {
        return ResponseEntity.ok(publicCatalogService.getPublished(id));
    }

    @GetMapping("/products/by-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> productByDpp(@PathVariable UUID dppFormId) {
        return ResponseEntity.ok(publicCatalogService.getPublishedByDpp(dppFormId));
    }

    @PostMapping("/products/{id}/view")
    ResponseEntity<Void> trackView(@PathVariable UUID id) {
        publicCatalogService.trackView(id);
        return ResponseEntity.accepted().build();
    }
}
