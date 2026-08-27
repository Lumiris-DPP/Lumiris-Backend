package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RepairabilityScoreServiceTest {

    private final RepairabilityScoreService service = new RepairabilityScoreService();

    private static DppScoreInput input(Boolean repairable, Integer warrantyMonths, String endOfLife,
                                       List<DppScoreInput.Material> materials, Set<DocumentType> docs) {
        return new DppScoreInput("France", "top", repairable, false, endOfLife,
                null, null, warrantyMonths, materials, DppScoreInput.Labels.NONE, docs);
    }

    private static DppScoreInput.Material material(String fiber, int percentage) {
        return new DppScoreInput.Material(fiber, percentage, "France", null, null);
    }

    @Test
    @DisplayName("un passeport vide ne rapporte rien")
    void emptyInput_scoresZero() {
        assertThat(service.compute(input(false, null, null, List.of(), Set.of()))).isZero();
    }

    @Test
    @DisplayName("cocher « réparable » sans manuel ne vaut plus la moitié de l'axe")
    void declarationAlone_isWorthLessThanDeclarationWithEvidence() {
        double declaredOnly = service.compute(input(true, null, null, List.of(), Set.of()));
        double documentedOnly = service.compute(input(false, null, null, List.of(), Set.of(DocumentType.REPAIR_MANUAL)));
        double both = service.compute(input(true, null, null, List.of(), Set.of(DocumentType.REPAIR_MANUAL)));

        assertThat(declaredOnly).isEqualTo(2);
        assertThat(documentedOnly).isEqualTo(2);
        assertThat(both).isEqualTo(5);
    }

    @Test
    @DisplayName("la garantie ne compte qu'à partir de 12 mois")
    void warranty_countsFromTwelveMonths() {
        assertThat(service.compute(input(false, 24, null, List.of(), Set.of()))).isEqualTo(2);
        assertThat(service.compute(input(false, 12, null, List.of(), Set.of()))).isEqualTo(2);
        assertThat(service.compute(input(false, 11, null, List.of(), Set.of()))).isZero();
    }

    @Test
    @DisplayName("les fibres faciles à repriser rapportent plus que les autres")
    void fiberScore_followsComposition() {
        double wool = service.compute(input(false, null, null, List.of(material("wool", 100)), Set.of()));
        double polyester = service.compute(
                input(false, null, null, List.of(material("recycled-polyester", 100)), Set.of()));

        assertThat(wool).isCloseTo(1.8, within(0.01));
        assertThat(polyester).isCloseTo(0.6, within(0.01));
    }

    @Test
    @DisplayName("la note fibre suit la part de chaque matière")
    void fiberScore_isWeightedByShare() {
        double mostlyWool = service.compute(
                input(false, null, null, List.of(material("wool", 90), material("recycled-polyester", 10)), Set.of()));
        double mostlyPolyester = service.compute(
                input(false, null, null, List.of(material("wool", 10), material("recycled-polyester", 90)), Set.of()));

        assertThat(mostlyWool).isGreaterThan(mostlyPolyester);
    }

    @Test
    @DisplayName("une consigne de fin de vie trop courte ne coche pas la case")
    void endOfLife_requiresAnActualInstruction() {
        assertThat(service.compute(input(false, null, "oui", List.of(), Set.of()))).isZero();
        assertThat(service.compute(
                input(false, null, "Rapporter en boutique pour recyclage textile.", List.of(), Set.of())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("l'axe plafonne bien à 10")
    void fullyDocumented_reachesAxisMaximum() {
        double score = service.compute(input(true, 24, "Rapporter en boutique pour recyclage textile.",
                List.of(material("wool", 100)), Set.of(DocumentType.REPAIR_MANUAL)));
        // 5 + 2 + 1,8 (laine) + 1 = 9,8 : le maximum théorique suppose une fibre parfaitement réparable.
        assertThat(score).isCloseTo(9.8, within(0.01)).isLessThanOrEqualTo(10);
    }
}
