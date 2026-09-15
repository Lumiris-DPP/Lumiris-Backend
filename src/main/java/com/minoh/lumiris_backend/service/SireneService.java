package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SireneService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public SireneService(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://recherche-entreprises.api.gouv.fr")
                .build();
    }

    public record SireneData(
            String companyName,
            String nafCode,
            String rawJson,
            String siren,
            String siegeAddress,
            String natureJuridique,
            String dirigeantsJson
    ) {}

    @Cacheable(cacheNames = "sirene", key = "#siret")
    public SireneData validate(String siret) {
        String rawJson = restClient.get()
                .uri("/search?q={siret}", siret)
                .retrieve()
                .body(String.class);

        JsonNode root;
        try {
            root = MAPPER.readTree(rawJson);
        } catch (Exception e) {
            throw new IllegalStateException("Réponse SIRENE illisible", e);
        }

        if (root.path("total_results").asInt() == 0) {
            throw new IllegalArgumentException("SIRET introuvable : " + siret);
        }

        JsonNode match = root.path("results").get(0);
        JsonNode siege = match.path("siege");
        JsonNode dirigeants = match.path("dirigeants");

        String companyName = diffusible(match.path("nom_complet").asText(null));
        if (companyName == null) {
            // L'INSEE masque tout champ identifiant (nom, adresse, dirigeants) par "[NON-DIFFUSIBLE]"
            // quand l'entreprise a demandé le secret de diffusion — fréquent chez les auto-entrepreneurs
            // dont la raison sociale est leur propre nom. On ne peut pas préremplir dans ce cas : il faut
            // laisser l'utilisateur saisir son nom d'atelier/affiché à la main.
            throw new IllegalArgumentException(
                    "Ce SIRET est en secret de diffusion auprès de l'INSEE : "
                            + "renseignez votre nom manuellement, il ne peut pas être récupéré automatiquement.");
        }

        return new SireneData(
                companyName,
                match.path("activite_principale").asText(null),
                match.toString(),
                match.path("siren").asText(null),
                diffusible(siege.path("adresse").asText(null)),
                match.path("nature_juridique").asText(null),
                dirigeants.isMissingNode() ? null : dirigeants.toString()
        );
    }

    // L'API "recherche-entreprises" ne masque pas les champs protégés : elle les remplace par le
    // texte littéral "[NON-DIFFUSIBLE]", qui passerait sinon pour une vraie valeur.
    private static String diffusible(String value) {
        return value == null || "[NON-DIFFUSIBLE]".equals(value) ? null : value;
    }
}
