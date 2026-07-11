package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public record SireneData(String companyName, String nafCode, String rawJson) {}

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
        return new SireneData(
                match.path("nom_complet").asText(null),
                match.path("activite_principale").asText(null),
                match.toString()
        );
    }
}
