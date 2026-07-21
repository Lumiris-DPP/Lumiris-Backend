package com.minoh.lumiris_backend.entity;

// Nouvelle demande (PENDING) -> devis soumis (DRAFT) -> ACCEPTED|REFUSED.
// REFUSED bascule automatiquement en COMPLETED. ACCEPTED -> IN_PROGRESS -> COMPLETED.
// Verrouillage unidirectionnel : aucun retour en arrière une fois IN_PROGRESS/COMPLETED.
public enum RepairRequestStatus {
    PENDING, DRAFT, ACCEPTED, REFUSED, IN_PROGRESS, COMPLETED
}
