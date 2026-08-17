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
    MESSAGE,
    // Bordereau d'expédition fabriqué par l'agrégateur (l'atelier n'a plus qu'à l'imprimer).
    LABEL_GENERATED,
    // Événement poussé par le TRANSPORTEUR. Distinct de SHIPPED : « expédiée » est ce que
    // l'atelier déclare, « pris en charge » est ce que le transporteur constate.
    TRACKING_UPDATE
}
