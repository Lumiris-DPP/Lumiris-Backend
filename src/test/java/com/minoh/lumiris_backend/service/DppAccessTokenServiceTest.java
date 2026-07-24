package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.DppAccessLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DppAccessTokenServiceTest {

    private DppAccessTokenService service;

    @BeforeEach
    void setUp() {
        service = new DppAccessTokenService();
        ReflectionTestUtils.setField(service, "secret", "test-secret-at-least-256-bits-long-for-hmac-sha256");
    }

    @Test
    void tokenFor_shouldBeStable_soALostQrCanSimplyBeReprinted() {
        assertThat(service.tokenFor("SEED0001", DppAccessLevel.AUTHORITIES))
                .isEqualTo(service.tokenFor("SEED0001", DppAccessLevel.AUTHORITIES));
    }

    @Test
    void tokenFor_shouldDifferPerLevelAndPerPassport() {
        String circular = service.tokenFor("SEED0001", DppAccessLevel.CIRCULAR_OPERATORS);
        String authorities = service.tokenFor("SEED0001", DppAccessLevel.AUTHORITIES);
        String otherPassport = service.tokenFor("SEED0002", DppAccessLevel.AUTHORITIES);

        assertThat(circular).isNotEqualTo(authorities).isNotEqualTo(otherPassport);
        assertThat(authorities).isNotEqualTo(otherPassport);
    }

    @Test
    void tokenFor_shouldReturnNothingForPublic() {
        assertThat(service.tokenFor("SEED0001", DppAccessLevel.PUBLIC)).isNull();
    }

    @Test
    void resolve_shouldRecogniseEachLevel() {
        for (DppAccessLevel level : new DppAccessLevel[] {
                DppAccessLevel.CIRCULAR_OPERATORS, DppAccessLevel.AUTHORITIES }) {
            assertThat(service.resolve("SEED0001", service.tokenFor("SEED0001", level))).isEqualTo(level);
        }
    }

    @Test
    void resolve_shouldFallBackToPublic_whenNoToken() {
        assertThat(service.resolve("SEED0001", null)).isEqualTo(DppAccessLevel.PUBLIC);
        assertThat(service.resolve("SEED0001", "  ")).isEqualTo(DppAccessLevel.PUBLIC);
    }

    @Test
    void resolve_shouldFallBackToPublic_whenTokenIsForged() {
        assertThat(service.resolve("SEED0001", "AAAAAAAAAAAAAAAA")).isEqualTo(DppAccessLevel.PUBLIC);
    }

    /** Le jeton d'un autre passeport ne doit rien ouvrir ici — c'est le rôle du code dans la signature. */
    @Test
    void resolve_shouldFallBackToPublic_whenTokenBelongsToAnotherPassport() {
        String other = service.tokenFor("SEED0002", DppAccessLevel.AUTHORITIES);

        assertThat(service.resolve("SEED0001", other)).isEqualTo(DppAccessLevel.PUBLIC);
    }

    @Test
    void resolve_shouldFallBackToPublic_whenSecretChanges() {
        String token = service.tokenFor("SEED0001", DppAccessLevel.AUTHORITIES);
        ReflectionTestUtils.setField(service, "secret", "a-completely-different-secret-value-256-bits");

        assertThat(service.resolve("SEED0001", token)).isEqualTo(DppAccessLevel.PUBLIC);
    }
}
