package com.minoh.lumiris_backend.entity;

// Cycle de vie d'une commande d'achat direct in-app.
public enum OrderStatus {
    PENDING,    // session Checkout créée, paiement non confirmé
    PAID,       // paiement confirmé (webhook) → fulfillment (Garde-Robe + facture)
    FULFILLED,  // pièce livrée / remise
    CANCELLED,
    REFUNDED
}
