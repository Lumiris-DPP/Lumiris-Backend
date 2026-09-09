package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;

// Une maille (~11 km) où des consommateurs cherchent un retoucheur sans en trouver.
public record CoverageGapResponse(double lat, double lng, long missCount, Instant lastSeen) {}
