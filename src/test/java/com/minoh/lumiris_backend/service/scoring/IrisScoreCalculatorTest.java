package com.minoh.lumiris_backend.service.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.dto.in.DppScoreInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class IrisScoreCalculatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final IrisScoreCalculator calculator = new IrisScoreCalculator(
            new TransparencyScoreService(),
            new CraftsmanshipScoreService(),
            new RepairabilityScoreService(),
            new ImpactScoreService());

    @Test
    @DisplayName("une charge utile partielle ne fait pas tomber le calcul")
    void partialPayload_doesNotThrow() throws Exception {
        // Forme héritée envoyée par d'anciens clients : ni materials, ni labels, ni documents.
        DppScoreInput input = mapper.readValue("""
                {"originCountry":"France","repairable":true}
                """, DppScoreInput.class);

        assertThatCode(() -> calculator.compute(input)).doesNotThrowAnyException();
        assertThat(input.materials()).isEmpty();
        assertThat(input.labels()).isEqualTo(DppScoreInput.Labels.NONE);
        assertThat(input.presentDocuments()).isEmpty();
    }

    @Test
    @DisplayName("un corps de requête ne peut pas s'attribuer un label")
    void labelsFromRequestBody_areOverridden() throws Exception {
        DppScoreInput forged = mapper.readValue("""
                {"labels":{"epv":true,"originFranceGarantie":true,"gots":true,"oekoTex":true}}
                """, DppScoreInput.class);

        double forgedScore = calculator.compute(forged).total();
        double resolvedScore = calculator.compute(forged.withLabels(DppScoreInput.Labels.NONE)).total();

        assertThat(forgedScore).isGreaterThan(resolvedScore);
        assertThat(resolvedScore).isZero();
    }

    @Test
    @DisplayName("un passeport vide ne bénéficie plus des 25 points offerts par l'impact")
    void emptyPassport_noLongerScoresTwentyFive() {
        DppScoreInput empty = new DppScoreInput(null, null, null, null, null,
                null, null, null, List.of(), DppScoreInput.Labels.NONE, java.util.Set.of());

        var result = calculator.compute(empty);
        assertThat(result.total()).isZero();
        assertThat(result.grade()).isEqualTo("E");
        assertThat(result.breakdown().impact()).isZero();
    }
}
