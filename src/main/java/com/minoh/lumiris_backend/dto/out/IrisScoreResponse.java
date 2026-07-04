package com.minoh.lumiris_backend.dto.out;

import java.util.List;

public record IrisScoreResponse(
        double total,
        String grade,
        Breakdown breakdown,
        Weights weights,
        List<Object> reasons
) {
    public record Breakdown(double transparency, double craftsmanship, double impact, double repairability) {}
    public record Weights(double transparency, double craftsmanship, double impact, double repairability) {}

    public static final Weights FIXED_WEIGHTS = new Weights(0.4, 0.25, 0.25, 0.1);
}
