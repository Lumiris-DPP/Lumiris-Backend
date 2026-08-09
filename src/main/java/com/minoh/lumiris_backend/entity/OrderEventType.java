package com.minoh.lumiris_backend.entity;

// Transitions journalisées d'une commande — alimente la timeline acheteur ET la piste d'audit.
public enum OrderEventType {
    ORDER_PLACED,
    PAYMENT_CONFIRMED,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURN_REFUSED,
    RETURN_RECEIVED,
    REFUNDED,
    DISPUTE_OPENED,
    DISPUTE_RESOLVED,
    DISPUTE_REJECTED,
    CANCELLED,
    FUNDS_RELEASED,
    // Fil de conversation acheteur ↔ atelier, ouvert à tout moment — pas seulement en litige.
    MESSAGE
}
