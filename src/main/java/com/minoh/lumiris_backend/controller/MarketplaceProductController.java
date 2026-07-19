package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.ConvertDppRequest;
import com.minoh.lumiris_backend.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.service.MarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// CRUD du catalogue produit — réservé à l'artisan authentifié (rôle ARTISAN).
@RestController
@RequestMapping("/api/marketplace/products")
@RequiredArgsConstructor
public class MarketplaceProductController {

    private final MarketplaceService marketplaceService;

    @GetMapping
    ResponseEntity<List<MarketplaceItemResponse>> listMine(@CurrentUserEmail String email) {
        return ResponseEntity.ok(marketplaceService.listMine(email));
    }

    @GetMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> getMine(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(marketplaceService.getMine(email, id));
    }

    @PostMapping("/from-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> convertFromDpp(@PathVariable UUID dppFormId,
                                                           @Valid @RequestBody ConvertDppRequest request,
                                                           @CurrentUserEmail String email) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(marketplaceService.convertFromDpp(email, dppFormId, request));
    }

    @PutMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateProductRequest request,
                                                   @CurrentUserEmail String email) {
        return ResponseEntity.ok(marketplaceService.update(email, id, request));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUserEmail String email) {
        marketplaceService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
