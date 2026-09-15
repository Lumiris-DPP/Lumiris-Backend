package com.minoh.lumiris_backend.dto.out;

import java.util.List;

// Données pré-remplies affichées au retoucheur qui suit un lien de réclamation, avant inscription.
public record RepairerClaimPreview(
        String displayName,
        String companyName,
        String siret,
        String address,
        String city,
        String region,
        List<String> specialties
) {}
