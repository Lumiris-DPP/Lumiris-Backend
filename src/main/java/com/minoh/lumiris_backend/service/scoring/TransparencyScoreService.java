package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.service.scoring.reference.CountryReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Transparence, 40 pts : à quel point la composition est complète, cohérente et justifiée.
 * Lignes de composition exploitables (fibre + % + pays) au prorata 16, divisé par deux si les
 * pourcentages ne totalisent pas 100 % · pays reconnus dans le référentiel au prorata 8 ·
 * certificats d'origine 5 et de transaction 5 · REACH déclaré 2, justifié 4.
 */
@Service
public class TransparencyScoreService {

    private static final double COMPOSITION_POINTS = 16;
    private static final double RECOGNISED_ORIGIN_POINTS = 8;

    public double compute(DppScoreInput input) {
        List<DppScoreInput.Material> materials = input.materials();

        return compositionScore(materials)
                + recognisedOriginScore(materials)
                + certificatesScore(input)
                + reachScore(input);
    }

    private double compositionScore(List<DppScoreInput.Material> materials) {
        if (materials.isEmpty()) return 0;

        long complete = materials.stream().filter(TransparencyScoreService::isComplete).count();
        double score = COMPOSITION_POINTS * complete / materials.size();

        double declaredShare = materials.stream()
                .mapToDouble(m -> m.percentage() == null ? 0 : m.percentage())
                .sum();
        return Math.abs(declaredShare - 100) < 1 ? score : score / 2;
    }

    private double recognisedOriginScore(List<DppScoreInput.Material> materials) {
        if (materials.isEmpty()) return 0;
        long recognised = materials.stream()
                .filter(m -> CountryReference.resolve(m.originCountry()).isPresent())
                .count();
        return RECOGNISED_ORIGIN_POINTS * recognised / materials.size();
    }

    private double certificatesScore(DppScoreInput input) {
        double score = 0;
        if (input.presentDocuments().contains(DocumentType.ORIGIN_CERTIFICATES)) score += 5;
        if (input.presentDocuments().contains(DocumentType.TRANSACTION_CERTIFICATES)) score += 5;
        return score;
    }

    private double reachScore(DppScoreInput input) {
        double score = Boolean.TRUE.equals(input.reachCompliant()) ? 2 : 0;
        if (input.presentDocuments().contains(DocumentType.REACH_COMPLIANCE)) score += 4;
        return score;
    }

    private static boolean isComplete(DppScoreInput.Material material) {
        return isFilled(material.fiber())
                && material.percentage() != null && material.percentage() > 0
                && isFilled(material.originCountry());
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
