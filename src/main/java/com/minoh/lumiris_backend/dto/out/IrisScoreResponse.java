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

    private static final Weights FIXED_WEIGHTS = new Weights(0.4, 0.25, 0.25, 0.1);

    public static IrisScoreResponse hardcoded() {
        return new IrisScoreResponse(32, "D",
                new Breakdown(18, 10, 0, 4),
                FIXED_WEIGHTS,
                List.of());
    }

    public static IrisScoreResponse random() {
        java.util.Random rng = new java.util.Random();
        double transparency  = rng.nextDouble() * 40;
        double craftsmanship = rng.nextDouble() * 25;
        double repairability = rng.nextDouble() * 10;
        double total         = transparency + craftsmanship + repairability;
        String grade = total >= 80 ? "A" : total >= 65 ? "B" : total >= 50 ? "C" : total >= 35 ? "D" : "E";
        return new IrisScoreResponse(
                Math.round(total * 10.0) / 10.0,
                grade,
                new Breakdown(
                        Math.round(transparency   * 10.0) / 10.0,
                        Math.round(craftsmanship  * 10.0) / 10.0,
                        0,
                        Math.round(repairability  * 10.0) / 10.0
                ),
                FIXED_WEIGHTS,
                List.of());
    }
}
