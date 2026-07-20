package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;

public record TrackEventRequest(
        @NotBlank String publicCode,
        @NotBlank String type
) {}
