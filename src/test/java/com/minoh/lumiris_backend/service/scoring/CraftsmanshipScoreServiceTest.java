package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CraftsmanshipScoreServiceTest {

    private final CraftsmanshipScoreService service = new CraftsmanshipScoreService();

    private static DppScoreInput input(String originCountry, Integer warrantyMonths,
                                       DppScoreInput.Labels labels, Set<DocumentType> docs) {
        return new DppScoreInput(originCountry, "top", false, false, null,
                null, null, warrantyMonths, List.of(), labels, docs);
    }

    @Test
    @DisplayName("un passeport vide ne rapporte rien")
    void emptyInput_scoresZero() {
        assertThat(service.compute(input(null, null, DppScoreInput.Labels.NONE, Set.of()))).isZero();
    }

    @Test
    @DisplayName("le pays est reconnu quelle que soit sa graphie")
    void originCountry_isMatchedRegardlessOfSpelling() {
        for (String spelling : List.of("France", "france", "FRANCE", " France ", "FR", "fr")) {
            assertThat(service.compute(input(spelling, null, DppScoreInput.Labels.NONE, Set.of())))
                    .as("graphie « %s »", spelling)
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("un pays étranger ne rapporte pas les points France")
    void foreignOrigin_scoresNoFranceBonus() {
        assertThat(service.compute(input("Portugal", null, DppScoreInput.Labels.NONE, Set.of()))).isZero();
    }

    @Test
    @DisplayName("les labels de l'atelier sont plafonnés à 13")
    void labels_areCapped() {
        var allLabels = new DppScoreInput.Labels(true, true, true, true);
        assertThat(service.compute(input(null, null, allLabels, Set.of()))).isEqualTo(13);
    }

    @Test
    @DisplayName("EPV pèse plus que les autres labels")
    void epv_weighsMost() {
        double epv = service.compute(input(null, null, new DppScoreInput.Labels(true, false, false, false), Set.of()));
        double gots = service.compute(input(null, null, new DppScoreInput.Labels(false, false, true, false), Set.of()));
        assertThat(epv).isEqualTo(8);
        assertThat(gots).isEqualTo(2);
    }

    @Test
    @DisplayName("la garantie est notée par palier")
    void warranty_scoresByTier() {
        assertThat(service.compute(input(null, 36, DppScoreInput.Labels.NONE, Set.of()))).isEqualTo(6);
        assertThat(service.compute(input(null, 24, DppScoreInput.Labels.NONE, Set.of()))).isEqualTo(6);
        assertThat(service.compute(input(null, 12, DppScoreInput.Labels.NONE, Set.of()))).isEqualTo(4);
        assertThat(service.compute(input(null, 6, DppScoreInput.Labels.NONE, Set.of()))).isEqualTo(2);
        assertThat(service.compute(input(null, 3, DppScoreInput.Labels.NONE, Set.of()))).isZero();
    }

    @Test
    @DisplayName("l'axe plafonne bien à 25")
    void fullyDocumented_reachesAxisMaximum() {
        var allLabels = new DppScoreInput.Labels(true, true, true, true);
        double score = service.compute(
                input("France", 24, allLabels, Set.of(DocumentType.CREATION_PASSPORT)));
        assertThat(score).isEqualTo(25);
    }
}
