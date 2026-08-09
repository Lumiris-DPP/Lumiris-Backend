package com.minoh.lumiris_backend.entity;

// Branche litige d'une commande, orthogonale au rail logistique (OrderStatus).
public enum DisputeStatus {
    NONE,
    OPEN,       // litige ouvert par l'acheteur, en attente d'arbitrage
    RESOLVED,   // tranché en faveur de l'acheteur (remboursement)
    REJECTED    // tranché en faveur du vendeur (aucun remboursement)
}
