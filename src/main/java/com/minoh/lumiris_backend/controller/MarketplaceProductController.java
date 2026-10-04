package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.in.ConvertDppRequest;
import com.minoh.lumiris_backend.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.service.SellerCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

// Reçoit les demandes de gestion des annonces artisan.
@RestController
@RequestMapping("/api/marketplace/products")
@RequiredArgsConstructor
public class MarketplaceProductController {

    private final SellerCatalogService sellerCatalogService;

    // Liste les annonces de l'artisan connecté.
    @GetMapping
    ResponseEntity<List<MarketplaceItemResponse>> listMine(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.listMine(email));
    }

    // Retrouve une annonce appartenant à l'artisan connecté.
    @GetMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> getMine(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.getMine(email, id));
    }

    // Met en vente une pièce depuis son passeport.
    @PostMapping("/from-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> convertFromDpp(@PathVariable UUID dppFormId,
                                                           @Valid @RequestBody ConvertDppRequest request,
                                                           @CurrentUserEmail String email) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerCatalogService.convertFromDpp(email, dppFormId, request));
    }

    // Enregistre les modifications de l'annonce de l'artisan connecté.
    @PutMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateProductRequest request,
                                                   @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.update(email, id, request));
    }

    // Supprime l'annonce désignée par l'artisan connecté.
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUserEmail String email) {
        sellerCatalogService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
