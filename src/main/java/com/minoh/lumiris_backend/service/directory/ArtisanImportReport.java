package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.ArtisanSource;

// Résultat d'un import : combien de fiches créées, rafraîchies, ignorées (déjà réclamées).
public record ArtisanImportReport(ArtisanSource source, int fetched, int created, int updated, int skipped) {}
