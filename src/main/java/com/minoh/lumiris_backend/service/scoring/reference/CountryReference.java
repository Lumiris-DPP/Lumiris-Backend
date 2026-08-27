package com.minoh.lumiris_backend.service.scoring.reference;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pays et centroïdes, en dur — portage de {@code packages/utils/src/countries.ts}.
 *
 * <p>Les pays sont saisis en texte libre dans le formulaire : la résolution accepte donc le code
 * ISO comme le libellé, insensible à la casse, aux accents et aux espaces. Sans cette
 * normalisation, une comparaison littérale échoue sur « FR », « france » ou « France ».
 */
public final class CountryReference {

    public record Country(String code, String label, double latitude, double longitude) {}

    private static final Map<String, Country> BY_KEY = new LinkedHashMap<>();

    private static void register(String code, String label, double latitude, double longitude) {
        Country country = new Country(code, label, latitude, longitude);
        BY_KEY.put(normalize(code), country);
        BY_KEY.put(normalize(label), country);
    }

    static {
        register("FR", "France", 46.2, 2.2);
        register("IT", "Italie", 42.8, 12.6);
        register("ES", "Espagne", 40.5, -3.7);
        register("PT", "Portugal", 39.4, -8.2);
        register("DE", "Allemagne", 51.2, 10.4);
        register("BE", "Belgique", 50.5, 4.5);
        register("NL", "Pays-Bas", 52.1, 5.3);
        register("GB", "Royaume-Uni", 54.0, -2.0);
        register("CH", "Suisse", 46.8, 8.2);
        register("PL", "Pologne", 51.9, 19.1);
        register("TR", "Turquie", 39.0, 35.2);
        register("MA", "Maroc", 31.8, -7.1);
        register("TN", "Tunisie", 33.9, 9.6);
        register("EG", "Égypte", 26.8, 30.8);
        register("IN", "Inde", 20.6, 79.0);
        register("CN", "Chine", 35.9, 104.2);
        register("BD", "Bangladesh", 23.7, 90.4);
        register("VN", "Viêt Nam", 14.1, 108.3);
        register("PK", "Pakistan", 30.4, 69.3);
        register("US", "États-Unis", 37.1, -95.7);
        register("BR", "Brésil", -14.2, -51.9);
    }

    public static Optional<Country> resolve(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_KEY.get(normalize(input)));
    }

    public static boolean isFrance(String input) {
        return resolve(input).map(c -> "FR".equals(c.code())).orElse(false);
    }

    /** Distance orthodromique en kilomètres. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String normalize(String value) {
        String stripped = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT);
    }

    private CountryReference() {}
}
