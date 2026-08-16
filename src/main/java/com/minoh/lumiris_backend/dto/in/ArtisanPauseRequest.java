package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// Mise en congés de l'atelier jusqu'à sa date de retour.
public record ArtisanPauseRequest(
        @NotNull @Future Instant until
) {}
