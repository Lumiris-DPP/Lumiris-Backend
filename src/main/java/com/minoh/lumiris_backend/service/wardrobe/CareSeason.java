package com.minoh.lumiris_backend.service.wardrobe;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

// Les deux seuls moments de l'année où l'on a quelque chose d'utile à dire sur une pièce rangée :
// on la sort, ou on la range. Hors de ces fenêtres, aucun rappel — un rappel d'entretien mensuel
// serait du bruit, et le bruit tue la légitimité des rares alertes qui comptent (rupture, garantie).
public enum CareSeason {
    // Mars → mai : la pièce d'hiver part au rangement, c'est le moment de la nettoyer.
    SPRING("Avant de la ranger pour l'été"),
    // Septembre → novembre : elle ressort, c'est le moment de vérifier son état.
    AUTUMN("Elle ressort pour la saison froide");

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final String occasion;

    CareSeason(String occasion) {
        this.occasion = occasion;
    }

    public String occasion() {
        return occasion;
    }

    // Clé annuelle stockée sur la pièce : le rappel doit revenir la saison suivante, ce qu'un
    // simple drapeau « déjà envoyé » éteindrait à vie.
    public String keyFor(Instant moment) {
        return ZonedDateTime.ofInstant(moment, ZONE).getYear() + "-" + name();
    }

    public static CareSeason at(Instant moment) {
        return switch (ZonedDateTime.ofInstant(moment, ZONE).getMonthValue()) {
            case 3, 4, 5 -> SPRING;
            case 9, 10, 11 -> AUTUMN;
            default -> null;
        };
    }
}
