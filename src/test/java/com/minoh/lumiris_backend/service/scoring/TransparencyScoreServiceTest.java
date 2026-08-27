package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import com.minoh.lumiris_backend.entity.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TransparencyScoreServiceTest {

    private final TransparencyScoreService service = new TransparencyScoreService();

    private static DppScoreInput input(List<DppScoreInput.Material> materials, Boolean reach, Set<DocumentType> docs) {
        return new DppScoreInput("France", "top", false, reach, null,
                null, null, null, materials, DppScoreInput.Labels.NONE, docs);
    }

    private static DppScoreInput.Material material(String fiber, Integer percentage, String origin) {
        return new DppScoreInput.Material(fiber, percentage, origin, null, null);
    }

    @Test
    @DisplayName("un passeport vide ne rapporte rien")
    void emptyInput_scoresZero() {
        assertThat(service.compute(input(List.of(), false, Set.of()))).isZero();
    }

    @Test
    @DisplayName("une composition complète et cohérente rapporte les 24 points de matière")
    void completeComposition_scoresCompositionAndOrigins() {
        var materials = List.of(material("linen", 60, "France"), material("cotton", 40, "Portugal"));
        assertThat(service.compute(input(materials, false, Set.of()))).isEqualTo(24);
    }

    @Test
    @DisplayName("la composition est notée au prorata des lignes exploitables")
    void partialComposition_scoresProportionally() {
        var materials = List.of(material("linen", 60, "France"), material("cotton", 40, null));
        // 1 ligne complète sur 2 → 8 pts, et 1 pays reconnu sur 2 → 4 pts.
        assertThat(service.compute(input(materials, false, Set.of()))).isEqualTo(12);
    }

    @Test
    @DisplayName("des pourcentages qui ne totalisent pas 100 % divisent le bloc composition par deux")
    void inconsistentPercentages_halveTheCompositionBlock() {
        var coherent = List.of(material("linen", 100, "France"));
        var incoherent = List.of(material("linen", 60, "France"));
        assertThat(service.compute(input(coherent, false, Set.of()))).isEqualTo(24);
        // Composition 16/2 = 8, origines reconnues 8 → 16.
        assertThat(service.compute(input(incoherent, false, Set.of()))).isEqualTo(16);
    }

    @Test
    @DisplayName("un pays fantaisiste ne vaut plus les points d'origine")
    void unrecognisedOrigin_scoresNoOriginPoints() {
        var materials = List.of(material("linen", 100, "Atlantide"));
        // La ligne reste « complète » (les trois champs sont remplis) mais le pays n'est pas reconnu.
        assertThat(service.compute(input(materials, false, Set.of()))).isEqualTo(16);
    }

    @Test
    @DisplayName("le justificatif REACH pèse le double de la déclaration")
    void reach_weighsEvidenceOverDeclaration() {
        var materials = List.<DppScoreInput.Material>of();
        assertThat(service.compute(input(materials, true, Set.of()))).isEqualTo(2);
        assertThat(service.compute(input(materials, false, Set.of(DocumentType.REACH_COMPLIANCE)))).isEqualTo(4);
        assertThat(service.compute(input(materials, true, Set.of(DocumentType.REACH_COMPLIANCE)))).isEqualTo(6);
    }

    @Test
    @DisplayName("l'axe plafonne bien à 40")
    void fullyDocumented_reachesAxisMaximum() {
        var materials = List.of(material("linen", 60, "France"), material("cotton", 40, "Portugal"));
        var docs = Set.of(DocumentType.ORIGIN_CERTIFICATES, DocumentType.TRANSACTION_CERTIFICATES,
                DocumentType.REACH_COMPLIANCE);
        assertThat(service.compute(input(materials, true, docs))).isEqualTo(40);
    }
}
