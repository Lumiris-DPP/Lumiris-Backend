package com.minoh.lumiris_backend.marketplace.favorite.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.marketplace.favorite.service.MarketplaceFavoriteService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Reçoit les demandes de gestion des pièces favorites.
@RestController
@RequestMapping("/api/marketplace/favorites")
@RequiredArgsConstructor
public class MarketplaceFavoriteController {

    private final MarketplaceFavoriteService favoriteService;

    // Liste les pièces favorites de l'utilisateur connecté.
    @GetMapping
    ResponseEntity<List<MarketplaceItemResponse>> list(@CurrentUserEmail String email) {
        return ResponseEntity.ok(favoriteService.list(email));
    }

    // Ajoute la pièce demandée aux favoris de l'utilisateur connecté.
    @PutMapping("/{productId}")
    ResponseEntity<Void> add(@PathVariable UUID productId, @CurrentUserEmail String email) {
        favoriteService.add(email, productId);
        return ResponseEntity.noContent().build();
    }

    // Retire la pièce demandée des favoris de l'utilisateur connecté.
    @DeleteMapping("/{productId}")
    ResponseEntity<Void> remove(@PathVariable UUID productId, @CurrentUserEmail String email) {
        favoriteService.remove(email, productId);
        return ResponseEntity.noContent().build();
    }
}
