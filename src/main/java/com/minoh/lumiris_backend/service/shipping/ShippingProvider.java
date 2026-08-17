package com.minoh.lumiris_backend.service.shipping;

import java.util.Optional;

// Frontière entre le cycle de vie d'une commande et l'agrégateur d'expédition. Le domaine ne
// connaît que ces trois questions ; le vocabulaire du prestataire (identifiants de méthode
// d'envoi, codes de statut, format de signature) ne franchit jamais cette ligne.
//
// `configured()` est la clé de la dégradation : sans clés d'API, l'atelier retrouve la saisie
// manuelle du suivi — chemin qui reste de toute façon nécessaire pour une remise en main propre
// ou un transporteur hors agrégateur.
public interface ShippingProvider {

    String name();

    boolean configured();

    ShippingLabel createLabel(ParcelRequest request);

    // Empty quand la charge utile ne concerne pas un changement d'état de colis (ping de test,
    // évènement d'un autre type) : ce n'est pas une erreur, il n'y a simplement rien à appliquer.
    Optional<CarrierEvent> readWebhook(String payload, String signature);
}
