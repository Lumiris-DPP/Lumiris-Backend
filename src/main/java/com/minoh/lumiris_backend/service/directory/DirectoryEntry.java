package com.minoh.lumiris_backend.service.directory;

// Une fiche retoucheur normalisée depuis une source d'annuaire, avant insertion en base.
public record DirectoryEntry(
        String externalRef,     // identifiant stable dans la source (SIRET, id OSM…)
        String displayName,
        String companyName,
        String siret,
        String address,
        String city,
        String region,
        Double latitude,        // null si la source ne géolocalise pas
        Double longitude,
        String rawJson
) {}
