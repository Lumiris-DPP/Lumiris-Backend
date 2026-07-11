package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.dto.out.IrisScoreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IrisScoreCalculator {

    private final TransparencyScoreService transparencyScoreService;
    private final CraftsmanshipScoreService craftsmanshipScoreService;
    private final RepairabilityScoreService repairabilityScoreService;
    private final ImpactScoreService impactScoreService;

    public IrisScoreResponse compute(DppScoreInput input) {
        double transparency   = transparencyScoreService.compute(input);
        double craftsmanship  = craftsmanshipScoreService.compute(input);
        double repairability  = repairabilityScoreService.compute(input);
        double impact         = impactScoreService.compute(input);
        double total          = transparency + craftsmanship + repairability + impact;

        var weights = IrisScoreResponse.FIXED_WEIGHTS;
        return new IrisScoreResponse(
                round(total),
                grade(total),
                new IrisScoreResponse.Breakdown(
                        round(transparency / weights.transparency()),
                        round(craftsmanship / weights.craftsmanship()),
                        round(impact / weights.impact()),
                        round(repairability / weights.repairability())
                ),
                weights,
                List.of()
        );
    }

    private static String grade(double total) {
        if (total >= 80) return "A";
        if (total >= 65) return "B";
        if (total >= 50) return "C";
        if (total >= 35) return "D";
        return "E";
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
