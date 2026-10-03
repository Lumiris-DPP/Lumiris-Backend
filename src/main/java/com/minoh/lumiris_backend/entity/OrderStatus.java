package com.minoh.lumiris_backend.entity;

import java.util.EnumSet;
import java.util.Set;

/** Décrit les états logistiques et les possibilités de retour. */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURN_REFUSED,
    RETURN_RECEIVED,
    REFUNDED,
    CANCELLED;

    private static final Set<OrderStatus> OPEN_FOR_SELLER = EnumSet.of(
            PAID, SHIPPED, DELIVERED, RETURN_REQUESTED, RETURN_APPROVED, RETURN_RECEIVED);

    private static final Set<OrderStatus> RETURNABLE = EnumSet.of(DELIVERED, SHIPPED);

    private static final Set<OrderStatus> TERMINAL = EnumSet.of(COMPLETED, REFUNDED, CANCELLED);
    private static final Set<OrderStatus> SOLD = EnumSet.of(
            PAID, SHIPPED, DELIVERED, COMPLETED, RETURN_REQUESTED, RETURN_APPROVED,
            RETURN_REFUSED, RETURN_RECEIVED);

    /** Renvoie les états comptabilisés comme ventes. */
    public static Set<OrderStatus> sold() {
        return SOLD;
    }

    /** Renvoie les états ouverts dans le tableau de bord vendeur. */
    public static Set<OrderStatus> openForSeller() {
        return OPEN_FOR_SELLER;
    }

    /** Indique si la commande a atteint un état final. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** Indique si une demande de retour est permise par l’état courant. */
    public boolean allowsReturnRequest() {
        return RETURNABLE.contains(this);
    }
}
