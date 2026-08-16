package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.out.ProductVariantResponse;
import com.minoh.lumiris_backend.dto.out.SizeMeasurementResponse;

import java.time.Instant;
import java.util.List;

// Tout ce qui, dans la vue d'une annonce, ne se lit pas sur l'entité produit : déclinaisons et
// guide chargés en lot, statut ATELIER+ résolu en lot, délai effectif combinant le délai annoncé
// et les congés de l'atelier.
// `variants` est obligatoire par construction — le stock exposé en est la somme, et un défaut à
// vide marquerait toute la boutique « Épuisé ».
public record ProductPresentation(
        List<ProductVariantResponse> variants,
        List<SizeMeasurementResponse> sizeGuide,
        boolean atelierPlus,
        int effectivePreparationDays,
        Instant atelierPausedUntil,
        long salesCount
) {}
