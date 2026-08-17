package com.minoh.lumiris_backend.service.wardrobe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CareSeasonTest {

    @Test
    @DisplayName("Le printemps couvre mars à mai — la pièce d'hiver part au rangement")
    void springWindow() {
        assertThat(CareSeason.at(Instant.parse("2026-03-01T09:00:00Z"))).isEqualTo(CareSeason.SPRING);
        assertThat(CareSeason.at(Instant.parse("2026-05-31T09:00:00Z"))).isEqualTo(CareSeason.SPRING);
    }

    @Test
    @DisplayName("L'automne couvre septembre à novembre — elle ressort pour la saison froide")
    void autumnWindow() {
        assertThat(CareSeason.at(Instant.parse("2026-09-01T09:00:00Z"))).isEqualTo(CareSeason.AUTUMN);
        assertThat(CareSeason.at(Instant.parse("2026-11-30T09:00:00Z"))).isEqualTo(CareSeason.AUTUMN);
    }

    @Test
    @DisplayName("Hors saison, aucun rappel : le bruit tuerait la crédibilité des alertes qui comptent")
    void offSeasonSendsNothing() {
        assertThat(CareSeason.at(Instant.parse("2026-01-15T09:00:00Z"))).isNull();
        assertThat(CareSeason.at(Instant.parse("2026-07-15T09:00:00Z"))).isNull();
        assertThat(CareSeason.at(Instant.parse("2026-12-20T09:00:00Z"))).isNull();
    }

    @Test
    @DisplayName("La clé porte l'année : le même rappel doit revenir la saison suivante")
    void keyRepeatsEveryYear() {
        assertThat(CareSeason.AUTUMN.keyFor(Instant.parse("2026-10-01T09:00:00Z"))).isEqualTo("2026-AUTUMN");
        assertThat(CareSeason.AUTUMN.keyFor(Instant.parse("2027-10-01T09:00:00Z"))).isEqualTo("2027-AUTUMN");
    }

    @Test
    @DisplayName("La saison est calculée à Paris, pas en UTC : un 1er mars à 00h30 locale est déjà le printemps")
    void keyUsesParisTime() {
        // 2026-02-28T23:30Z == 2026-03-01T00:30 à Paris (UTC+1 en février).
        assertThat(CareSeason.at(Instant.parse("2026-02-28T23:30:00Z"))).isEqualTo(CareSeason.SPRING);
    }
}
