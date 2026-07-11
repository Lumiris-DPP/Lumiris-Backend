package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import org.springframework.stereotype.Service;

@Service
public class CraftsmanshipScoreService {

    public double compute(DppScoreInput input) {
        double score = 0;
        if ("france".equalsIgnoreCase(input.originCountry())) score += 10;
        if (input.presentDocuments().contains(DocumentType.CREATION_PASSPORT)) score += 15;
        return score;
    }
}
