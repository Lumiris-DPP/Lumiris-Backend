package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ArtisanPublicProfileResponse(
        String slug,
        String displayName,
        String atelierName,
        String story,
        String method,
        String journey,
        List<String> specialties,
        String city,
        String region,
        String websiteUrl,
        Map<String, String> links,
        List<String> photoUrls,
        boolean epvLabeled,
        boolean ofgLabeled,
        boolean gotsLabeled,
        boolean oekoTexLabeled,
        Instant pausedUntil,
        // false = fiche annuaire sans compte : bandeau "pas encore dans le réseau" côté front.
        boolean claimed,
        int interestCount,
        // null pour les ateliers SELF (pas d'adresse structurée) : invisibles sur la carte, restent
        // en liste. Non-null pour les imports SIRENE géocodés.
        Double lat,
        Double lng
) {}
