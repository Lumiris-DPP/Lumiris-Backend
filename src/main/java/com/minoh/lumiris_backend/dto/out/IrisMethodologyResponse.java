package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Texte de transparence sur le score Iris V2, prêt à afficher.
 * Le front n'interprète rien : il rend {@code sections} dans l'ordre reçu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IrisMethodologyResponse(
        String version,
        String title,
        String intro,
        List<Section> sections,
        List<GradeScale> grades,
        String disclaimer
) {
    /**
     * Un axe du score. {@code weightPercent} est null pour ce qui ne pèse pas dans la
     * moyenne pondérée (plafond réglementaire).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(
            String key,
            String label,
            Integer weightPercent,
            String what,
            List<String> howToImprove
    ) {}

    /** Palier de la lettre Iris : {@code minScore} est le total pondéré minimum requis. */
    public record GradeScale(String grade, int minScore, String label) {}
}
