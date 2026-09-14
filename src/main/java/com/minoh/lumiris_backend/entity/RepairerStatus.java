package com.minoh.lumiris_backend.entity;

public enum RepairerStatus {
    // Compte réel en attente de revue KYB.
    PENDING,
    VERIFIED,
    REJECTED,
    // Fiche importée d'un annuaire, sans compte, non vérifiée — visible en recherche publique
    // (pattern "Doctolib" : bandeau "pas encore dans le réseau" + CTA doux) mais avec un jeu de
    // champs réduit et sans les stats propres à un compte réel (avis, délai de réponse…).
    // Réclamable via un jeton.
    UNCLAIMED,
    // Compte vérifié puis retiré du réseau (manquement, cessation d'activité).
    SUSPENDED
}
