package com.minoh.lumiris_backend.dto.in;

import java.util.List;

public record RepairerProfileUpdateRequest(
        String displayName,
        List<String> specialties,
        List<String> zones,
        String schedule,
        String address,
        String city,
        String region
) {}
