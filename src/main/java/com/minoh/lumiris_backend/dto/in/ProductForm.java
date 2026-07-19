package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;

import java.util.UUID;

// Contrat commun aux formulaires de création et de mise à jour d'un produit du
// catalogue : mêmes champs, mêmes contraintes. Permet au mapper de partager la
// recopie des champs sans dupliquer (la sémantique de `status` reste propre à chaque cas).
public interface ProductForm {

    String name();

    String description();

    String category();

    String material();

    String originCountry();

    int priceCents();

    String currency();

    int stock();

    String externalOrderUrl();

    String photoUrl();

    UUID dppFormId();

    MarketplaceProductStatus status();
}
