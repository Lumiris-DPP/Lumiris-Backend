package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ArtisanRegisterRequest(
        @NotBlank
        @Pattern(regexp = "\\d{14}", message = "SIRET must be exactly 14 digits")
        String siret
) {}
