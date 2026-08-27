package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.DppForm;

import java.util.List;
import java.util.Set;

public record DppScoreInput(
        String originCountry,
        String productCategory,
        Boolean repairable,
        Boolean reachCompliant,
        String endOfLifeInstructions,
        Integer weightGrams,
        Integer recycledPct,
        Integer warrantyMonths,
        List<Material> materials,
        Labels labels,
        Set<DocumentType> presentDocuments
) {
    public DppScoreInput {
        materials = materials == null ? List.of() : materials;
        labels = labels == null ? Labels.NONE : labels;
        presentDocuments = presentDocuments == null ? Set.of() : presentDocuments;
    }

    public record Material(String fiber, Integer percentage, String originCountry,
                           Double latitude, Double longitude) {}

    public record Labels(boolean epv, boolean originFranceGarantie, boolean gots, boolean oekoTex) {
        public static final Labels NONE = new Labels(false, false, false, false);
    }

    public static DppScoreInput from(DppForm form, Set<DocumentType> docs) {
        List<Material> materials = form.getMaterials().stream()
                .map(m -> new Material(m.getFiber(), m.getPercentage(), m.getOriginCountry(),
                        m.getLatitude(), m.getLongitude()))
                .toList();
        return new DppScoreInput(
                form.getOriginCountry(),
                form.getProductCategory(),
                form.getIsRepairable(),
                form.getReachCompliant(),
                form.getEndOfLifeInstructions(),
                form.getWeightGrams(),
                form.getRecycledPct(),
                form.getWarrantyMonths(),
                materials,
                labelsOf(form),
                docs
        );
    }

    public DppScoreInput withLabels(Labels resolved) {
        return new DppScoreInput(originCountry, productCategory, repairable, reachCompliant,
                endOfLifeInstructions, weightGrams, recycledPct, warrantyMonths,
                materials, resolved, presentDocuments);
    }

    public static Labels labelsOf(DppForm form) {
        ArtisanProfile profile = form.getUser() == null ? null : form.getUser().getArtisanProfile();
        if (profile == null) return Labels.NONE;
        return new Labels(profile.isEpvLabeled(), profile.isOfgLabeled(),
                profile.isGotsLabeled(), profile.isOekoTexLabeled());
    }
}
