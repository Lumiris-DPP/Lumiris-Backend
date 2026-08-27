package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.service.scoring.reference.CountryReference;
import com.minoh.lumiris_backend.service.scoring.reference.FiberReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

/**
 * Impact, 25 pts : l'empreinte du vêtement, moyenne de quatre sous-scores de poids égal.
 * Carbone et eau (empreinte fibre × poids, plafonds 12 kgCO₂e et 3 000 L) · matière recyclée
 * (cible 50 %) · transport (distance origine → confection pondérée par fibre, plafond 2 000 km).
 * Un sous-score dont les données manquent est exclu de la moyenne ; sans composition, l'axe vaut 0.
 */
@Service
public class ImpactScoreService {

    private static final double AXIS_POINTS = 25.0;
    private static final double CARBON_CEILING_KG = 12.0;
    private static final double WATER_CEILING_LITERS = 3000.0;
    private static final double RECYCLED_TARGET_PCT = 50.0;
    private static final double TRANSPORT_CEILING_KM = 2000.0;

    public double compute(DppScoreInput input) {
        List<DppScoreInput.Material> materials = input.materials();
        double declaredShare = materials.stream()
                .mapToDouble(m -> m.percentage() == null ? 0 : m.percentage())
                .sum();
        if (declaredShare <= 0) {
            return 0;
        }

        List<Double> subScores = new ArrayList<>();

        Double massKg = massKg(input);
        if (massKg != null) {
            subScores.add(footprintScore(materials, declaredShare, massKg,
                    FiberReference.Coefficients::carbonKgPerKg, CARBON_CEILING_KG));
            subScores.add(footprintScore(materials, declaredShare, massKg,
                    FiberReference.Coefficients::waterLitersPerKg, WATER_CEILING_LITERS));
        }
        subScores.add(recycledScore(input, materials, declaredShare));
        transportScore(input, materials, declaredShare).ifPresent(subScores::add);

        double average = subScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return round(AXIS_POINTS * average);
    }

    private Double massKg(DppScoreInput input) {
        Integer grams = input.weightGrams();
        return grams == null ? null : grams / 1000.0;
    }

    private double footprintScore(List<DppScoreInput.Material> materials, double declaredShare, double massKg,
                                  ToDoubleFunction<FiberReference.Coefficients> coefficient, double ceiling) {
        double footprint = materials.stream()
                .mapToDouble(m -> share(m, declaredShare)
                        * coefficient.applyAsDouble(FiberReference.of(m.fiber()))
                        * massKg)
                .sum();
        return clamp(1 - footprint / ceiling);
    }

    private double recycledScore(DppScoreInput input, List<DppScoreInput.Material> materials, double declaredShare) {
        double pct = input.recycledPct() != null
                ? input.recycledPct()
                : recycledShareFromFibers(materials, declaredShare);
        return clamp(pct / RECYCLED_TARGET_PCT);
    }

    private double recycledShareFromFibers(List<DppScoreInput.Material> materials, double declaredShare) {
        return materials.stream()
                .filter(m -> m.fiber() != null && m.fiber().trim().toLowerCase(Locale.ROOT).startsWith("recycled"))
                .mapToDouble(m -> share(m, declaredShare) * 100)
                .sum();
    }

    private OptionalDouble transportScore(DppScoreInput input, List<DppScoreInput.Material> materials,
                                          double declaredShare) {
        var destination = CountryReference.resolve(input.originCountry());
        if (destination.isEmpty()) {
            return OptionalDouble.empty();
        }
        CountryReference.Country to = destination.get();

        double weightedKm = 0;
        double locatedShare = 0;
        for (DppScoreInput.Material material : materials) {
            double[] from = coordinatesOf(material);
            if (from == null) continue;
            double share = share(material, declaredShare);
            weightedKm += share * CountryReference.distanceKm(from[0], from[1], to.latitude(), to.longitude());
            locatedShare += share;
        }
        if (locatedShare <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(clamp(1 - (weightedKm / locatedShare) / TRANSPORT_CEILING_KM));
    }

    private double[] coordinatesOf(DppScoreInput.Material material) {
        if (material.latitude() != null && material.longitude() != null) {
            return new double[]{material.latitude(), material.longitude()};
        }
        return CountryReference.resolve(material.originCountry())
                .map(c -> new double[]{c.latitude(), c.longitude()})
                .orElse(null);
    }

    private double share(DppScoreInput.Material material, double declaredShare) {
        double percentage = material.percentage() == null ? 0 : material.percentage();
        return percentage / declaredShare;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
