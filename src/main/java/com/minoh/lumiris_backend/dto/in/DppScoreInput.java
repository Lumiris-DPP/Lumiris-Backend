package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppMaterial;

import java.util.List;
import java.util.Set;

public record DppScoreInput(
        String originCountry,
        Boolean repairable,
        Boolean reachCompliant,
        String endOfLifeInstructions,
        List<String> materialOriginCountries,
        Set<DocumentType> presentDocuments
) {
    public static DppScoreInput from(DppForm form, Set<DocumentType> docs) {
        List<String> origins = form.getMaterials().stream()
                .map(DppMaterial::getOriginCountry)
                .toList();
        return new DppScoreInput(
                form.getOriginCountry(),
                form.getIsRepairable(),
                form.getReachCompliant(),
                form.getEndOfLifeInstructions(),
                origins,
                docs
        );
    }

    public static DppScoreInput from(DppFormRequest req, Set<DocumentType> docs) {
        List<String> origins = req.materials() == null ? List.of() :
                req.materials().stream().map(MaterialRequest::originCountry).toList();
        return new DppScoreInput(
                req.originCountry(),
                req.isRepairable(),
                req.reachCompliant(),
                req.endOfLifeInstructions(),
                origins,
                docs
        );
    }
}
