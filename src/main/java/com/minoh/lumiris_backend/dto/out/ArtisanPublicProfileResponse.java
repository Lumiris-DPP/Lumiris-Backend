package com.minoh.lumiris_backend.dto.out;

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
        boolean oekoTexLabeled
) {}
