package com.minoh.lumiris_backend.marketplace.catalog.service;

import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;

import java.util.UUID;

// Associe une pièce à son score de fabrication.
public record ScoredProduct(MarketplaceProduct product, IrisScore score) {

    // Retrouve le compte de l'atelier associé à la pièce.
    public UUID artisanUserId() {
        return product.getArtisanProfile().getUser().getId();
    }

    // Fournit le score total ou zéro si absent.
    public double total() {
        return score != null ? score.getTotal() : Double.NEGATIVE_INFINITY;
    }
}
