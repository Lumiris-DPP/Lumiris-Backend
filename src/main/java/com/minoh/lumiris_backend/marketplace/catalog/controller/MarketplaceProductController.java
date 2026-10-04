package com.minoh.lumiris_backend.marketplace.catalog.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.ConvertDppRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.marketplace.catalog.service.SellerCatalogService;
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

@RestController
@RequestMapping("/api/marketplace/products")
@RequiredArgsConstructor
public class MarketplaceProductController {

    private final SellerCatalogService sellerCatalogService;

    @GetMapping
    ResponseEntity<List<MarketplaceItemResponse>> listMine(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.listMine(email));
    }

    @GetMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> getMine(@PathVariable UUID id, @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.getMine(email, id));
    }

    @PostMapping("/from-dpp/{dppFormId}")
    ResponseEntity<MarketplaceItemResponse> convertFromDpp(@PathVariable UUID dppFormId,
                                                           @Valid @RequestBody ConvertDppRequest request,
                                                           @CurrentUserEmail String email) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerCatalogService.convertFromDpp(email, dppFormId, request));
    }

    @PutMapping("/{id}")
    ResponseEntity<MarketplaceItemResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateProductRequest request,
                                                   @CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerCatalogService.update(email, id, request));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUserEmail String email) {
        sellerCatalogService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
