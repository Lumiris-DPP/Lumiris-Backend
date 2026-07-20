package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Texte de transparence sur le calcul du score Iris V2, prêt à afficher.
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
     * Une catégorie de la méthodologie. {@code weightPercent} est null pour les sections
     * qui ne pèsent pas dans la moyenne pondérée (plafond réglementaire, barème).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(
            String key,
            String label,
            Integer weightPercent,
            String summary,
            List<Criterion> criteria
    ) {}

    /** Un critère au sein d'une catégorie ; {@code points} est sur 100 à l'échelle de la catégorie. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Criterion(String label, String description, Integer points) {}

    /** Palier de la lettre Iris : {@code minScore} est le total pondéré minimum requis. */
    public record GradeScale(String grade, int minScore, String label) {}
}
