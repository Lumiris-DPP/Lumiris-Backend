package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// fiber et percentage sont NOT NULL en base : sans ces contraintes, l'oubli finit en 500.
public record MaterialRequest(
        @NotBlank @Size(max = 100) String fiber,
        @NotNull @Min(0) @Max(100) Integer percentage,
        @Size(max = 100) String originCountry
) {}
