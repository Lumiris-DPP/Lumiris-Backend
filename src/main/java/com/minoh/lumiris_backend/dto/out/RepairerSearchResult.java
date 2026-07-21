package com.minoh.lumiris_backend.dto.out;

import java.util.List;
import java.util.UUID;

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
        double distanceKm
) {}
