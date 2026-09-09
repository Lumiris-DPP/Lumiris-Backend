package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepairerSearchResult(
        UUID id,
        String displayName,
        String companyName,
        List<String> specialties,
        List<String> zones,
        String schedule,
        String address,
        String city,
        String region,
        double distanceKm,
        double lat,
        double lng,
        Double averageRating,
        long reviewCount,
        // Délai médian demande -> devis, en heures (null si aucun devis).
        Double medianResponseHours,
        // false = fiche annuaire sans compte : afficher un CTA doux plutôt qu'une prise de RDV.
        boolean claimed
) {}
