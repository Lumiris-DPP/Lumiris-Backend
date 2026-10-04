package com.minoh.lumiris_backend.marketplace.catalog.service;

import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;

import java.util.UUID;

public record ScoredProduct(MarketplaceProduct product, IrisScore score) {

    public UUID artisanUserId() {
        return product.getArtisanProfile().getUser().getId();
    }

    public double total() {
        return score != null ? score.getTotal() : Double.NEGATIVE_INFINITY;
    }
}
