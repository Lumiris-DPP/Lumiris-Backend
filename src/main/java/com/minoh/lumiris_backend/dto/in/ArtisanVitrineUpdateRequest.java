package com.minoh.lumiris_backend.dto.in;

import java.util.List;
import java.util.Map;

public record ArtisanVitrineUpdateRequest(
        String atelierName,
        String story,
        String method,
        String journey,
        List<String> specialties,
        String city,
        String region,
        String websiteUrl,
        Map<String, String> links
) {}
