package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactScoreServiceTest {

    private final ImpactScoreService service = new ImpactScoreService();

    private static DppScoreInput input(Integer weightGrams, Integer recycledPct, String originCountry,
                                       List<DppScoreInput.Material> materials) {
        return new DppScoreInput(originCountry, "outerwear", false, false, null,
                weightGrams, recycledPct, null, materials, DppScoreInput.Labels.NONE, Set.of());
    }

    private static DppScoreInput.Material material(String fiber, int percentage, String origin) {
        return new DppScoreInput.Material(fiber, percentage, origin, null, null);
    }

    @Test
    @DisplayName("sans composition, l'axe vaut 0 et non le maximum")
    void emptyComposition_scoresZero() {
        assertThat(service.compute(input(500, 100, "France", List.of()))).isZero();
    }

    @Test
    @DisplayName("des pourcentages tous nuls valent une composition absente")
    void zeroPercentages_scoreZero() {
        assertThat(service.compute(input(500, 50, "France", List.of(material("cotton", 0, "France"))))).isZero();
    }

    @Test
    @DisplayName("lin français léger, 100 % recyclé : proche du maximum")
    void lowImpactGarment_scoresHigh() {
        double score = service.compute(input(300, 100, "France",
                List.of(material("linen", 100, "France"))));
        assertThat(score).isGreaterThan(24.0).isLessThanOrEqualTo(25.0);
    }

    @Test
    @DisplayName("cachemire lourd venu de Chine, sans recyclé : proche de zéro")
    void highImpactGarment_scoresLow() {
        double score = service.compute(input(1200, 0, "France",
                List.of(material("cashmere", 100, "Chine"))));
        assertThat(score).isLessThan(5.0);
    }

    @Test
    @DisplayName("sans poids, carbone et eau sortent du calcul sans pénaliser")
    void missingWeight_excludesFootprintInsteadOfPenalising() {
        List<DppScoreInput.Material> materials = List.of(material("cashmere", 100, "France"));
        double withWeight = service.compute(input(1200, 100, "France", materials));
        double withoutWeight = service.compute(input(null, 100, "France", materials));

        // Recyclé au maximum et transport nul : sans poids l'axe est plein, alors que le cachemire
        // lourd effondrerait carbone et eau.
        assertThat(withoutWeight).isEqualTo(25.0);
        assertThat(withWeight).isLessThan(withoutWeight);
    }

    @Test
    @DisplayName("un pays d'origine inconnu retire le transport du calcul")
    void unresolvableOrigin_excludesTransport() {
        List<DppScoreInput.Material> materials = List.of(material("linen", 100, "Atlantide"));
        assertThat(service.compute(input(null, 0, "Terre du Milieu", materials))).isZero();
        // Recyclé seul, à 0 : l'axe vaut 0 sans que le transport ne soit compté comme parfait.
    }

    @Test
    @DisplayName("une composition qui ne somme pas à 100 % ne minore pas l'empreinte")
    void partialComposition_isRenormalised() {
        double full = service.compute(input(1000, 0, "France", List.of(material("cashmere", 100, "France"))));
        double half = service.compute(input(1000, 0, "France", List.of(material("cashmere", 50, "France"))));
        assertThat(half).isEqualTo(full);
    }

    @Test
    @DisplayName("le transport est pondéré par la part de chaque fibre")
    void transport_isWeightedByShare() {
        double mostlyLocal = service.compute(input(null, 0, "France",
                List.of(material("linen", 95, "France"), material("cotton", 5, "Chine"))));
        double mostlyFar = service.compute(input(null, 0, "France",
                List.of(material("linen", 5, "France"), material("cotton", 95, "Chine"))));
        assertThat(mostlyLocal).isGreaterThan(mostlyFar);
    }

    @Test
    @DisplayName("recycledPct absent se déduit des fibres recyclées")
    void recycledShare_fallsBackToFibers() {
        double score = service.compute(input(null, null, "France",
                List.of(material("recycled-polyester", 50, "France"), material("cotton", 50, "France"))));
        // 50 % recyclé = cible atteinte, transport nul : axe plein.
        assertThat(score).isEqualTo(25.0);
    }
}
