package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;

import java.util.List;
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

    Integer shippingCents();

    String returnPolicy();

    Integer preparationDays();

    List<ProductVariantForm> variants();

    List<SizeMeasurementForm> sizeGuide();

    String externalOrderUrl();

    String photoUrl();

    UUID dppFormId();

    MarketplaceProductStatus status();
}
