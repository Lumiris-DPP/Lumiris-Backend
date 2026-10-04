package com.minoh.lumiris_backend.marketplace.catalog.dto.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Porte les critères des suggestions de pièces.
public record SuggestRequest(
        String category,
        @NotNull @PositiveOrZero Double score,
        String grade,
        String material
) {}
