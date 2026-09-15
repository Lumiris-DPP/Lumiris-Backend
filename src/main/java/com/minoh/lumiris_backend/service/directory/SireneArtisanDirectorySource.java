package com.minoh.lumiris_backend.service.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.entity.ArtisanSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Import depuis l'API « Recherche d'entreprises » (recherche-entreprises.api.gouv.fr) — ouverte,
 * sans authentification. Mêmes mécaniques que {@link SireneDirectorySource} (retoucheurs) : filtre
 * NAF + département, pagine (25 résultats/page) jusqu'à {@code maxPages}.
 */
@Slf4j
@Component
public class SireneArtisanDirectorySource implements ArtisanDirectorySource {

    // Fabrication de vêtements de dessus (créateurs de mode), maroquinerie/sellerie, autres
    // vêtements et accessoires. Codes NAF Rév. 2 réels (cf. la liste renvoyée par /search en 400).
    private static final List<String> DEFAULT_NAF = List.of("14.13Z", "15.12Z", "14.19Z");
    private static final int PER_PAGE = 25;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient restClient;

    public SireneArtisanDirectorySource(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://recherche-entreprises.api.gouv.fr").build();
    }

    @Override
    public ArtisanSource source() {
        return ArtisanSource.SIRENE;
    }

    @Override
    public List<DirectoryEntry> fetch(ImportCriteria criteria) {
        List<String> nafCodes = criteria.nafCodes().isEmpty() ? DEFAULT_NAF : criteria.nafCodes();
        List<String> departments = criteria.departments().isEmpty() ? Collections.singletonList(null) : criteria.departments();

        List<DirectoryEntry> out = new ArrayList<>();
        for (String naf : nafCodes) {
            for (String dept : departments) {
                out.addAll(fetchSlice(naf, dept, criteria.maxPages()));
            }
        }
        return out;
    }

    private List<DirectoryEntry> fetchSlice(String naf, String department, int maxPages) {
        List<DirectoryEntry> entries = new ArrayList<>();
        for (int page = 1; page <= maxPages; page++) {
            JsonNode root = call(naf, department, page);
            if (root == null) {
                break;
            }
            JsonNode results = root.path("results");
            for (JsonNode match : results) {
                DirectoryEntry entry = toEntry(match);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (page >= root.path("total_pages").asInt(1) || results.isEmpty()) {
                break;
            }
        }
        return entries;
    }

    private JsonNode call(String naf, String department, int page) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/search")
                                .queryParam("activite_principale", naf)
                                .queryParam("per_page", PER_PAGE)
                                .queryParam("page", page);
                        if (department != null) {
                            b = b.queryParam("departement", department);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(String.class);
            return MAPPER.readTree(body);
        } catch (Exception e) {
            log.warn("Import SIRENE artisans: page {} (NAF {}, dept {}) ignorée: {}", page, naf, department, e.getMessage());
            return null;
        }
    }

    private DirectoryEntry toEntry(JsonNode match) {
        JsonNode siege = match.path("siege");
        String siret = siege.path("siret").asText(null);
        if (siret == null || siret.isBlank()) {
            return null;
        }
        String name = diffusible(firstNonBlank(match.path("nom_complet").asText(null),
                match.path("nom_raison_sociale").asText(null)));
        // Entreprise en secret de diffusion INSEE : nom, adresse et coordonnées valent tous
        // littéralement "[NON-DIFFUSIBLE]" — une fiche sans nom ni localisation n'a aucune valeur
        // pour l'annuaire public, on la saute plutôt que de publier le texte de masquage tel quel.
        if (name == null) {
            return null;
        }

        boolean active = !"F".equalsIgnoreCase(
                firstNonBlank(siege.path("etat_administratif").asText(null),
                        match.path("etat_administratif").asText(null)));

        return new DirectoryEntry(
                siret,
                name,
                diffusible(match.path("nom_raison_sociale").asText(name)),
                siret,
                diffusible(siege.path("adresse").asText(null)),
                diffusible(siege.path("libelle_commune").asText(null)),
                blankToNull(diffusible(siege.path("region").asText(null))),
                parseDouble(siege.path("latitude").asText(null)),
                parseDouble(siege.path("longitude").asText(null)),
                active,
                match.toString()
        );
    }

    // L'API "recherche-entreprises" ne masque pas les champs protégés : elle les remplace par le
    // texte littéral "[NON-DIFFUSIBLE]", qui passerait sinon pour une vraie valeur.
    private static String diffusible(String value) {
        return value == null || "[NON-DIFFUSIBLE]".equals(value) ? null : value;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
