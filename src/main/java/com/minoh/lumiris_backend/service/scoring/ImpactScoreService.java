package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import org.springframework.stereotype.Service;

@Service
public class ImpactScoreService {

    public double compute(DppScoreInput input) {
        return 25.0;
    }
}
