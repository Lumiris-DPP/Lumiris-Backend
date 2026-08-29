package com.minoh.lumiris_backend.entity;

// Familles de notification. Le libellé et le lien sont portés par la notification elle-même :
// ce type ne sert qu'au regroupement, au filtrage et au choix de la sévérité côté UI.
public enum NotificationType {
    ORDER_PAID,
    ORDER_TO_SHIP,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_COMPLETED,
    ORDER_CANCELLED,
    ORDER_MESSAGE,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURN_REFUSED,
    RETURN_RECEIVED,
    ORDER_REFUNDED,
    DISPUTE_OPENED,
    DISPUTE_RESOLVED,
    DISPUTE_REJECTED,
    FUNDS_RELEASED,
    FAVORITE_LOW_STOCK,
    FAVORITE_PRICE_DROP,
    // Garde-Robe active : les seuls rappels qu'on puisse envoyer après la livraison sans rien
    // vendre — l'entretien tiré du passeport et la garantie qui s'achève.
    WARDROBE_CARE,
    WARDROBE_WARRANTY_ENDING,
    CERTIFICATE_EXPIRING,
    RETOUCH_ACCEPTED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PASSPORT_PUBLISHED,
    PASSPORT_SCANNED
}
