package com.minoh.lumiris_backend.entity;

import java.util.EnumMap;
import java.util.Map;

// Regroupement user-facing des NotificationType, plus grossier — c'est la granularité à laquelle
// un utilisateur choisit de se désabonner (côté /me/settings), pas type par type.
public enum NotificationCategory {
    ORDERS,
    RETURNS_DISPUTES,
    FAVORITES,
    WARDROBE,
    PAYMENTS,
    PASSPORT;

    private static final Map<NotificationType, NotificationCategory> BY_TYPE = new EnumMap<>(NotificationType.class);

    static {
        BY_TYPE.put(NotificationType.ORDER_PAID, ORDERS);
        BY_TYPE.put(NotificationType.ORDER_TO_SHIP, ORDERS);
        BY_TYPE.put(NotificationType.ORDER_SHIPPED, ORDERS);
        BY_TYPE.put(NotificationType.ORDER_DELIVERED, ORDERS);
        BY_TYPE.put(NotificationType.ORDER_COMPLETED, ORDERS);
        BY_TYPE.put(NotificationType.ORDER_CANCELLED, ORDERS);
        BY_TYPE.put(NotificationType.ORDER_MESSAGE, ORDERS);
        BY_TYPE.put(NotificationType.ORDER_REFUNDED, ORDERS);
        BY_TYPE.put(NotificationType.FUNDS_RELEASED, ORDERS);

        BY_TYPE.put(NotificationType.RETURN_REQUESTED, RETURNS_DISPUTES);
        BY_TYPE.put(NotificationType.RETURN_APPROVED, RETURNS_DISPUTES);
        BY_TYPE.put(NotificationType.RETURN_REFUSED, RETURNS_DISPUTES);
        BY_TYPE.put(NotificationType.RETURN_RECEIVED, RETURNS_DISPUTES);
        BY_TYPE.put(NotificationType.DISPUTE_OPENED, RETURNS_DISPUTES);
        BY_TYPE.put(NotificationType.DISPUTE_RESOLVED, RETURNS_DISPUTES);
        BY_TYPE.put(NotificationType.DISPUTE_REJECTED, RETURNS_DISPUTES);

        BY_TYPE.put(NotificationType.FAVORITE_LOW_STOCK, FAVORITES);
        BY_TYPE.put(NotificationType.FAVORITE_PRICE_DROP, FAVORITES);

        BY_TYPE.put(NotificationType.WARDROBE_CARE, WARDROBE);
        BY_TYPE.put(NotificationType.WARDROBE_WARRANTY_ENDING, WARDROBE);

        BY_TYPE.put(NotificationType.PAYMENT_SUCCEEDED, PAYMENTS);
        BY_TYPE.put(NotificationType.PAYMENT_FAILED, PAYMENTS);

        BY_TYPE.put(NotificationType.PASSPORT_PUBLISHED, PASSPORT);
        BY_TYPE.put(NotificationType.PASSPORT_SCANNED, PASSPORT);
    }

    // Volontairement stricte : un NotificationType oublié ici doit casser au lieu de silencieusement
    // échapper à toute préférence de désabonnement.
    public static NotificationCategory of(NotificationType type) {
        NotificationCategory category = BY_TYPE.get(type);
        if (category == null) {
            throw new IllegalStateException("Aucune NotificationCategory mappée pour " + type);
        }
        return category;
    }
}
