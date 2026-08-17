package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;

import java.time.Instant;
import java.util.List;
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
        // Somme des stocks des déclinaisons : le stock ne vit que sur la déclinaison.
        int stock,
        List<ProductVariantResponse> variants,
        List<SizeMeasurementResponse> sizeGuide,
        // Livraison + retour + garantie : visibles AVANT le paiement (transparence, moins d'abandon panier).
        int shippingCents,
        String returnPolicy,
        String warrantyDescription,
        // preparationDays est la valeur BRUTE saisie par l'artisan (celle que son formulaire
        // réenregistre) ; effectivePreparationDays est ce qui est promis à l'acheteur, congés de
        // l'atelier inclus. Exposer une seule valeur graverait la rallonge de congés dans le produit
        // au premier enregistrement du formulaire.
        int preparationDays,
        int effectivePreparationDays,
        Instant atelierPausedUntil,
        // Poids du colis : donnée d'atelier, réémise telle quelle par son formulaire. Sans elle,
        // aucun bordereau n'est fabricable pour cette annonce.
        int weightGrams,
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
