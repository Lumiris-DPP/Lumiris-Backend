package com.minoh.lumiris_backend.service.scoring.reference;

import java.util.Locale;
import java.util.Map;

/**
 * Coefficients par fibre, en dur : le score doit être calculable hors ligne et reproductible.
 * Carbone en kgCO₂e/kg et eau en L/kg proviennent de la Base Empreinte ADEME 2024, du Higg MSI
 * et du Water Footprint Network — mêmes valeurs que {@code packages/core/src/scoring/constants.ts}.
 * `repairability` est une note 0–1 : à quel point la fibre se reprise et se retouche.
 */
public final class FiberReference {

    public record Coefficients(double carbonKgPerKg, double waterLitersPerKg, double repairability) {}

    private static final Coefficients OTHER = new Coefficients(8, 3000, 0.5);

    private static final Map<String, Coefficients> BY_FIBER = Map.of(
            "wool",                new Coefficients(22, 600, 0.9),
            "linen",               new Coefficients(0.5, 200, 0.8),
            "cotton",              new Coefficients(5, 8000, 0.8),
            "silk",                new Coefficients(18, 4000, 0.4),
            "hemp",                new Coefficients(0.4, 150, 0.8),
            "leather",             new Coefficients(17, 17000, 0.6),
            "cashmere",            new Coefficients(28, 6500, 0.9),
            "recycled-polyester",  new Coefficients(2.4, 50, 0.3)
    );

    public static Coefficients of(String fiber) {
        if (fiber == null) return OTHER;
        return BY_FIBER.getOrDefault(fiber.trim().toLowerCase(Locale.ROOT), OTHER);
    }

    private FiberReference() {}
}
