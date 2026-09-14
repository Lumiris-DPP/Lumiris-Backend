package com.minoh.lumiris_backend.service.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.entity.RepairerSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Import depuis l'API « Recherche d'entreprises » (recherche-entreprises.api.gouv.fr) — ouverte,
 * sans authentification, avec la géolocalisation du siège. On filtre par code NAF et département,
 * on pagine (25 résultats/page) jusqu'à {@code maxPages}.
 */
@Slf4j
@Component
public class SireneDirectorySource implements RepairerDirectorySource {

    // Cordonnerie / réparation d'articles personnels — inclut la retouche textile. NAF Rév. 2 réel
    // (95.29A/95.29B n'existent pas dans la nomenclature — /search les rejette avec 400).
    private static final List<String> DEFAULT_NAF = List.of("95.23Z", "95.29Z");
    private static final int PER_PAGE = 25;

    // 95.23Z = réparation de chaussures et d'articles en cuir — cordonnerie (spécifique, on garde
    // tout). 95.29Z est large (attrape aussi réparation de montres, de vélos…) : on n'y garde que
    // les raisons sociales évoquant la retouche / couture / cordonnerie.
    private static final Set<String> ALWAYS_KEEP_NAF = Set.of("95.23Z");
    private static final Pattern TEXTILE_KEYWORDS = Pattern.compile(
            "retouch|coutur|cordonn|tailleu|couseu|maroquin|repris|ourlet|piqu[eè]", Pattern.CASE_INSENSITIVE);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient restClient;

    public SireneDirectorySource(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://recherche-entreprises.api.gouv.fr").build();
    }

    @Override
    public RepairerSource source() {
        return RepairerSource.SIRENE;
    }

    @Override
    public List<DirectoryEntry> fetch(ImportCriteria criteria) {
        List<String> nafCodes = criteria.nafCodes().isEmpty() ? DEFAULT_NAF : criteria.nafCodes();
        // List.of(null) throws (it rejects null elements) — Collections.singletonList allows it,
        // and null here means "no department filter" (fetchSlice/call skip the query param).
        List<String> departments =
                criteria.departments().isEmpty() ? Collections.singletonList(null) : criteria.departments();

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
                DirectoryEntry entry = toEntry(match, naf);
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
            log.warn("Import SIRENE: page {} (NAF {}, dept {}) ignorée: {}", page, naf, department, e.getMessage());
            return null;
        }
    }

    private DirectoryEntry toEntry(JsonNode match, String naf) {
        JsonNode siege = match.path("siege");
        String siret = siege.path("siret").asText(null);
        if (siret == null || siret.isBlank()) {
            return null;
        }
        String name = firstNonBlank(match.path("nom_complet").asText(null),
                match.path("nom_raison_sociale").asText(null));

        // Filtre anti-bruit : sur un code NAF large, on n'importe que si la raison sociale évoque
        // clairement la retouche / couture / cordonnerie.
        if (!ALWAYS_KEEP_NAF.contains(naf) && (name == null || !TEXTILE_KEYWORDS.matcher(name).find())) {
            return null;
        }

        boolean active = !"F".equalsIgnoreCase(
                firstNonBlank(siege.path("etat_administratif").asText(null),
                        match.path("etat_administratif").asText(null)));

        return new DirectoryEntry(
                siret,
                name,
                match.path("nom_raison_sociale").asText(name),
                siret,
                siege.path("adresse").asText(null),
                siege.path("libelle_commune").asText(null),
                blankToNull(siege.path("region").asText(null)),
                parseDouble(siege.path("latitude").asText(null)),
                parseDouble(siege.path("longitude").asText(null)),
                active,
                match.toString()
        );
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
