package com.minoh.lumiris_backend.entity;

public enum RepairerStatus {
    // Compte réel en attente de revue KYB.
    PENDING,
    VERIFIED,
    REJECTED,
    // Fiche importée d'un annuaire, sans compte, non vérifiée — masquée de la recherche
    // publique tant qu'un admin ne l'a pas promue. Réclamable via un jeton.
    UNCLAIMED,
    // Compte vérifié puis retiré du réseau (manquement, cessation d'activité).
    SUSPENDED
}
