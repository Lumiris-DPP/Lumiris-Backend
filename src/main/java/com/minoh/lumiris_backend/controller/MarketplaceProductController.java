package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.ConvertDppRequest;
import com.minoh.lumiris_backend.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.service.SellerCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// CRUD du catalogue produit — réservé à l'artisan authentifié (rôle ARTISAN).
@RestController
@RequestMapping("/api/marketplace/products")
@RequiredArgsConstructor
public class MarketplaceProductController {

    private final SellerCatalogService sellerCatalogService;

    // Annonces de l'atelier connecté, avec leurs ventes réglées.
    @GetMapping
    ResponseEntity<List<MarketplaceItemResponse>> listMine(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.listMine(email));
    }

    // Une annonce de l'atelier connecté, quel que soit son statut.
    @GetMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> getMine(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.getMine(email, id));
    }

    // Mise en vente d'une pièce à passeport valide (201 avec l'annonce créée ou réécrite).
    @PostMapping("/from-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> convertFromDpp(@PathVariable UUID dppFormId,
                                                           @Valid @RequestBody ConvertDppRequest request,
                                                           @CurrentUserEmail String email) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerCatalogService.convertFromDpp(email, dppFormId, request));
    }

    // Remplacement complet d'une annonce : champs, déclinaisons et guide des mesures.
    @PutMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateProductRequest request,
                                                   @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.update(email, id, request));
    }

    // Suppression d'une annonce jamais vendue (409 sinon : l'archiver).
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUserEmail String email) {
        sellerCatalogService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
