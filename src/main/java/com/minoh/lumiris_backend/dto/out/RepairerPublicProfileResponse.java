package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepairerPublicProfileResponse(
        UUID id,
        String displayName,
        String companyName,
        List<String> specialties,
        List<String> zones,
        String schedule,
        String address,
        String city,
        String region,
        Double averageRating,
        long reviewCount,
        // Délai médian entre la demande et l'envoi du devis (heures), null si aucun devis encore.
        Double medianResponseHours,
        // Nombre d'interventions terminées avec un devis (proxy d'expérience).
        long completedJobs
) {}
