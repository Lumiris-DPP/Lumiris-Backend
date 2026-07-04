package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import org.springframework.stereotype.Service;

@Service
public class RepairabilityScoreService {

    public double compute(DppScoreInput input) {
        double score = 0;
        if (Boolean.TRUE.equals(input.repairable())) score += 5;
        if (input.presentDocuments().contains(DocumentType.REPAIR_MANUAL)) score += 3;
        if (input.endOfLifeInstructions() != null && !input.endOfLifeInstructions().isBlank()) score += 2;
        return score;
    }
}
