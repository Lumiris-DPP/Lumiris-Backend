package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Entrée du moteur de suggestions : les attributs du DPP scanné. Le DPP scanné peut
// être hors backend (scan mobile), donc on passe son score/catégorie, pas un id.
// On renvoie JUSQU'À 3 alternatives artisanales de score Iris >= score (le seuil de
// score n'est jamais abaissé : s'il existe moins de 3 pièces qualifiées, on en renvoie moins).
public record SuggestRequest(
        String category,
        @NotNull @PositiveOrZero Double score,
        String grade,
        String material
) {}
