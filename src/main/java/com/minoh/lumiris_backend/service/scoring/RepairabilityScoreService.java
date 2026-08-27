package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.service.scoring.reference.FiberReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Réparabilité, 10 pts : la possibilité concrète de faire réparer le vêtement.
 * Réparable déclaré et manuel fourni 5, l'un des deux seulement 2 · garantie ≥ 12 mois 2 ·
 * réparabilité des fibres pondérée par la composition 2 · consignes de fin de vie 1.
 */
@Service
public class RepairabilityScoreService {

    private static final double FIBER_POINTS = 2;
    private static final int MIN_END_OF_LIFE_LENGTH = 20;

    public double compute(DppScoreInput input) {
        return claimScore(input)
                + (input.warrantyMonths() != null && input.warrantyMonths() >= 12 ? 2 : 0)
                + fiberScore(input.materials())
                + (isSubstantial(input.endOfLifeInstructions()) ? 1 : 0);
    }

    private double claimScore(DppScoreInput input) {
        boolean declared = Boolean.TRUE.equals(input.repairable());
        boolean documented = input.presentDocuments().contains(DocumentType.REPAIR_MANUAL);
        if (declared && documented) return 5;
        return declared || documented ? 2 : 0;
    }

    private double fiberScore(List<DppScoreInput.Material> materials) {
        double declaredShare = materials.stream()
                .mapToDouble(m -> m.percentage() == null ? 0 : m.percentage())
                .sum();
        if (declaredShare <= 0) return 0;

        double weighted = materials.stream()
                .mapToDouble(m -> (m.percentage() == null ? 0 : m.percentage()) / declaredShare
                        * FiberReference.of(m.fiber()).repairability())
                .sum();
        return FIBER_POINTS * weighted;
    }

    private static boolean isSubstantial(String instructions) {
        return instructions != null && instructions.trim().length() >= MIN_END_OF_LIFE_LENGTH;
    }
}
