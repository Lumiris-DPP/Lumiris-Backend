package com.minoh.lumiris_backend.entity;

import java.util.EnumSet;
import java.util.Set;

// Cycle de vie d'une commande marketplace (LUMIRIS-24). Rail principal : payée → expédiée →
// livrée → clôturée, avec une branche retour. Le litige est porté par DisputeStatus : il est
// orthogonal (une commande expédiée peut être en litige sans quitter son état logistique).
public enum OrderStatus {
    PENDING,           // PaymentIntent créé, paiement non confirmé
    PAID,              // paiement confirmé (webhook) → à expédier
    SHIPPED,           // expédiée, suivi saisi par le vendeur
    DELIVERED,         // livrée (confirmée par l'acheteur ou présumée) → fenêtre de retour ouverte
    COMPLETED,         // clôturée : fenêtre de retour écoulée, plus de retour possible
    RETURN_REQUESTED,  // retour demandé par l'acheteur dans la fenêtre
    RETURN_APPROVED,   // retour accepté : l'acheteur renvoie la pièce
    RETURN_REFUSED,    // retour refusé par le vendeur (l'acheteur peut ouvrir un litige)
    RETURN_RECEIVED,   // colis retour reçu par le vendeur → remboursement attendu
    REFUNDED,          // remboursée (totale ou partielle, cf. refundedCents)
    CANCELLED;

    private static final Set<OrderStatus> OPEN_FOR_SELLER = EnumSet.of(
            PAID, SHIPPED, DELIVERED, RETURN_REQUESTED, RETURN_APPROVED, RETURN_RECEIVED);

    private static final Set<OrderStatus> RETURNABLE = EnumSet.of(DELIVERED, SHIPPED);

    private static final Set<OrderStatus> TERMINAL = EnumSet.of(COMPLETED, REFUNDED, CANCELLED);

    // La vente compte dans le chiffre d'affaires du vendeur (encaissée, non remboursée).
    private static final Set<OrderStatus> SOLD = EnumSet.of(
            PAID, SHIPPED, DELIVERED, COMPLETED, RETURN_REQUESTED, RETURN_APPROVED,
            RETURN_REFUSED, RETURN_RECEIVED);

    public static Set<OrderStatus> sold() {
        return SOLD;
    }

    public static Set<OrderStatus> openForSeller() {
        return OPEN_FOR_SELLER;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    // Un retour ne s'ouvre qu'après expédition et tant que la commande n'est pas clôturée.
    public boolean allowsReturnRequest() {
        return RETURNABLE.contains(this);
    }
}
