package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import org.springframework.stereotype.Service;

@Service
public class TransparencyScoreService {

    public double compute(DppScoreInput input) {
        double score = 0;
        if (input.presentDocuments().contains(DocumentType.ORIGIN_CERTIFICATES)) score += 10;
        if (input.presentDocuments().contains(DocumentType.TRANSACTION_CERTIFICATES)) score += 10;
        if (!input.materialOriginCountries().isEmpty()
                && input.materialOriginCountries().stream().allMatch(o -> o != null && !o.isBlank())) score += 10;
        if (Boolean.TRUE.equals(input.reachCompliant())) score += 5;
        if (input.presentDocuments().contains(DocumentType.REACH_COMPLIANCE)) score += 5;
        return score;
    }
}
