package com.minoh.lumiris_backend.entity;

public enum ArtisanStatus {
    PENDING, VERIFIED, REJECTED,
    // Fiche importée d'un annuaire (SIRENE), sans compte — pattern "Doctolib" : visible en
    // recherche/vitrine publique avec un jeu de champs réduit, bandeau "pas encore dans le
    // réseau" côté front. Réclamable via un jeton (voir RepairerStatus.UNCLAIMED, même logique).
    UNCLAIMED
}
