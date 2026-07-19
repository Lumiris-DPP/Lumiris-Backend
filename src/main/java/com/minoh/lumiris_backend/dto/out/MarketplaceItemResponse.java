package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;

import java.time.Instant;
import java.util.UUID;

// Vue canonique d'un produit du catalogue (CRUD, recherche, suggestions).
// irisTotal/irisGrade proviennent du DPP lié ; atelierPlus est résolu à la volée.
public record MarketplaceItemResponse(
        UUID id,
        UUID artisanProfileId,
        String artisanName,
        UUID dppFormId,
        String name,
        String description,
        String category,
        String material,
        String originCountry,
        int priceCents,
        String currency,
        int stock,
        String externalOrderUrl,
        String photoUrl,
        MarketplaceProductStatus status,
        Double irisTotal,
        String irisGrade,
        boolean atelierPlus,
        boolean inAppSale,
        Instant createdAt,
        // Statistiques vendeur (0 sur les chemins publics search/suggest).
        long views,
        long salesCount
) {}
