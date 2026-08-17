package com.minoh.lumiris_backend.service.wardrobe;

import com.minoh.lumiris_backend.entity.DppCareInstruction;
import com.minoh.lumiris_backend.entity.DppForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CareAdviceResolverTest {

    private CareAdviceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CareAdviceResolver();
    }

    @Test
    @DisplayName("Sans symbole au passeport, aucun conseil : on n'invente pas un entretien générique")
    void noSymbolsNoAdvice() {
        assertThat(resolver.adviceFor(formWith())).isNull();
    }

    @Test
    @DisplayName("Un passeport absent ne fait pas échouer le balayage")
    void nullFormIsTolerated() {
        assertThat(resolver.adviceFor(null)).isNull();
    }

    @Test
    @DisplayName("Un code inconnu du vocabulaire est ignoré plutôt qu'affiché brut")
    void unknownCodeIsIgnored() {
        assertThat(resolver.adviceFor(formWith("wash-90"))).isNull();
    }

    @Test
    @DisplayName("Deux conseils au maximum, et ce qui interdit passe avant ce qui recommande")
    void keepsTheTwoMostImportantTips() {
        String advice = resolver.adviceFor(formWith("iron-low", "no-tumble", "no-wash", "wash-30"));

        assertThat(advice).isEqualTo("pas de machine : nettoyage à sec ou à l'éponge, puis un lavage à 30 °C");
    }

    @Test
    @DisplayName("L'ordre de saisie de l'atelier ne change pas la priorité des conseils")
    void orderOfCodesDoesNotMatter() {
        assertThat(resolver.adviceFor(formWith("no-iron", "dry-clean")))
                .isEqualTo(resolver.adviceFor(formWith("dry-clean", "no-iron")));
    }

    private static DppForm formWith(String... careCodes) {
        DppForm form = new DppForm();
        form.setCareInstructions(Arrays.stream(careCodes).map(code -> {
            DppCareInstruction instruction = new DppCareInstruction();
            instruction.setCareCode(code);
            return instruction;
        }).toList());
        return form;
    }
}
