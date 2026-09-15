package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.RepairerSource;

// Résultat d'un import : combien de fiches créées, rafraîchies, ignorées (déjà réclamées).
public record ImportReport(RepairerSource source, int fetched, int created, int updated, int skipped) {}
