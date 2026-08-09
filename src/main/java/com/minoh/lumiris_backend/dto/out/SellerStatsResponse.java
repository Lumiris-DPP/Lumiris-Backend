package com.minoh.lumiris_backend.dto.out;

// LUMIRIS-22 · Tableau de bord vendeur (ATELIER) : agrégats des ventes directes in-app.
// netCents = ce que l'atelier reçoit réellement (grossCents - commission plateforme ~5%).
public record SellerStatsResponse(
        long salesCount,        // ventes réglées (encaissées, non remboursées)
        long grossCents,        // chiffre d'affaires brut encaissé
        long commissionCents,   // part plateforme (~5%)
        long netCents,          // payout net versé à l'atelier
        long wardrobeCount,     // pièces entrées dans la Garde-Robe d'acheteurs
        long totalViews,        // vues cumulées des fiches produit
        long productCount,      // annonces au catalogue
        long publishedCount     // annonces publiées (visibles en Boutique)
) {}
