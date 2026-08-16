package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.service.MarketplaceFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Favoris de l'acheteur authentifié. PUT et non POST : l'identifiant est connu du client et
// l'opération est idempotente — un double tap ne peut pas créer deux lignes.
@RestController
@RequestMapping("/api/marketplace/favorites")
@RequiredArgsConstructor
public class MarketplaceFavoriteController {

    private final MarketplaceFavoriteService favoriteService;

    @GetMapping
    ResponseEntity<List<MarketplaceItemResponse>> list(@CurrentUserEmail String email) {
        return ResponseEntity.ok(favoriteService.list(email));
    }

    @PutMapping("/{productId}")
    ResponseEntity<Void> add(@PathVariable UUID productId, @CurrentUserEmail String email) {
        favoriteService.add(email, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    ResponseEntity<Void> remove(@PathVariable UUID productId, @CurrentUserEmail String email) {
        favoriteService.remove(email, productId);
        return ResponseEntity.noContent().build();
    }
}
