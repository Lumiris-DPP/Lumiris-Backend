package com.minoh.lumiris_backend.marketplace.catalog.dto.in;


import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;

import java.util.List;
import java.util.UUID;

// Contrat commun aux formulaires de création et de mise à jour d'un produit du
// catalogue : mêmes champs, mêmes contraintes. Permet au mapper de partager la
// recopie des champs sans dupliquer (la sémantique de `status` reste propre à chaque cas).
public interface ProductForm {

    /** Fournit le nom saisi pour l’annonce. */
    String name();

    /** Fournit la description saisie pour l’annonce. */
    String description();

    /** Fournit la catégorie saisie pour l’annonce. */
    String category();

    /** Fournit la matière saisie pour l’annonce. */
    String material();

    /** Fournit le pays d’origine saisi pour l’annonce. */
    String originCountry();

    /** Fournit le prix de vente en centimes. */
    int priceCents();

    /** Fournit la devise du prix annoncé. */
    String currency();

    /** Fournit les frais d’expédition saisis pour l’annonce. */
    Integer shippingCents();

    /** Fournit les conditions de retour annoncées par l’atelier. */
    String returnPolicy();

    /** Fournit le délai de préparation annoncé par l’atelier. */
    Integer preparationDays();

    // Poids du colis, en grammes : c'est là-dessus que le transporteur tarife, et sans lui aucun
    // bordereau n'est fabricable. Absent ⇒ le poids par défaut configuré prend le relais.
    Integer weightGrams();

    /** Fournit les déclinaisons à enregistrer avec l’annonce. */
    List<ProductVariantForm> variants();

    /** Fournit les mesures du guide de tailles à enregistrer. */
    List<SizeMeasurementForm> sizeGuide();

    /** Fournit l’URL de commande externe lorsqu’elle est renseignée. */
    String externalOrderUrl();

    /** Fournit l’URL de la photo principale de l’annonce. */
    String photoUrl();

    /** Fournit l’identifiant du passeport à lier à l’annonce. */
    UUID dppFormId();

    /** Fournit le statut demandé selon le formulaire de publication ou de mise à jour. */
    MarketplaceProductStatus status();
}
