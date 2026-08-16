package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;

import java.util.UUID;

// Tuple (produit, score du DPP lié) renvoyé par toutes les requêtes du catalogue. Le score
// comparable provient exclusivement du passeport, jamais d'un champ dénormalisé.
public record ScoredProduct(MarketplaceProduct product, IrisScore score) {

    public UUID artisanUserId() {
        return product.getArtisanProfile().getUser().getId();
    }

    public double total() {
        return score != null ? score.getTotal() : Double.NEGATIVE_INFINITY;
    }
}
