package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.service.scoring.reference.CountryReference;
import org.springframework.stereotype.Service;

/**
 * Savoir-faire, 25 pts : ce que l'atelier apporte et ce qui l'atteste.
 * Labels EPV 8 / OFG 5 / GOTS 2 / OEKO-TEX 2, plafonnés à 13 · garantie 6, 4 ou 2 selon la durée ·
 * carnet de création 4 · fabrication en France 2.
 */
@Service
public class CraftsmanshipScoreService {

    private static final double LABELS_CAP = 13;

    public double compute(DppScoreInput input) {
        return labelsScore(input.labels())
                + warrantyScore(input.warrantyMonths())
                + (input.presentDocuments().contains(DocumentType.CREATION_PASSPORT) ? 4 : 0)
                + (CountryReference.isFrance(input.originCountry()) ? 2 : 0);
    }

    private double labelsScore(DppScoreInput.Labels labels) {
        double score = 0;
        if (labels.epv()) score += 8;
        if (labels.originFranceGarantie()) score += 5;
        if (labels.gots()) score += 2;
        if (labels.oekoTex()) score += 2;
        return Math.min(LABELS_CAP, score);
    }

    private double warrantyScore(Integer months) {
        if (months == null) return 0;
        if (months >= 24) return 6;
        if (months >= 12) return 4;
        if (months >= 6) return 2;
        return 0;
    }
}
