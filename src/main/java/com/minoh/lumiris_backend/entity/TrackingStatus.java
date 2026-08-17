package com.minoh.lumiris_backend.entity;

// Ce que le TRANSPORTEUR constate, par opposition à OrderStatus qui dit où en est la commande.
// Chaque agrégateur a son propre vocabulaire (des dizaines d'états, parfois par transporteur) :
// l'adaptateur les ramène à ces six-là, et conserve à côté le libellé brut pour l'affichage.
//
// Seul DELIVERED fait avancer la commande — c'est le fait qui ouvre la fenêtre de rétractation et
// libère les fonds. Les autres enrichissent la timeline sans rien décider.
public enum TrackingStatus {
    ANNOUNCED,        // bordereau créé, colis pas encore remis au transporteur
    IN_TRANSIT,       // pris en charge, en cours d'acheminement
    OUT_FOR_DELIVERY, // en cours de livraison ou disponible en point relais
    DELIVERED,        // livré — seul état qui fait avancer la commande
    EXCEPTION,        // incident : adresse erronée, colis endommagé, livraison échouée
    RETURNED;         // renvoyé à l'expéditeur

    public boolean isDelivered() {
        return this == DELIVERED;
    }
}
