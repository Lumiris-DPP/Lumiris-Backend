package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.entity.DppStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "DppPublicJsonLd", description = "Représentation JSON-LD publique d'un passeport produit")
public record DppPublicJsonLdResponse(
        @JsonProperty("@context") Map<String, Object> context,
        @JsonProperty("@id") String id,
        @JsonProperty("@type") List<String> type,

        String publicCode,
        Instant dateCreated,
        DppStatus status,

        String name,
        String description,
        String category,
        Country countryOfOrigin,
        List<String> size,
        List<String> color,
        String image,

        List<Material> materials,
        List<String> careInstructions,
        String careNotes,

        String productionDate,
        String batchNumber,
        String gtin,
        Boolean reachCompliant,
        Integer weightGrams,
        Integer recycledPercentage,
        String warrantyDescription,
        Integer warrantyMonths,
        Boolean repairable,
        String endOfLifeInstructions,
        Integer productionQuantity,

        String dataHash,
        BlockchainAnchorStatus blockchainAnchorStatus,
        String blockchainTransactionHash,

        List<Document> documents,
        IrisScoreResponse irisScore,
        String artisanSlug
) {
    public record Material(
            String fiber,
            Integer percentage,
            Country countryOfOrigin,
            Double latitude,
            Double longitude
    ) {}

    public record Country(
            @JsonProperty("@type") String type,
            String name
    ) {}

    public record Document(
            @JsonProperty("@type") String type,
            String documentType,
            String name,
            String url
    ) {}
}
