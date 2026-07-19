package com.minoh.lumiris_backend.entity;

// Cycle de vie d'un produit du catalogue artisan. Seuls les PUBLISHED entrent
// dans la recherche publique et le moteur de suggestions.
public enum MarketplaceProductStatus {
    DRAFT, PUBLISHED, ARCHIVED
}
