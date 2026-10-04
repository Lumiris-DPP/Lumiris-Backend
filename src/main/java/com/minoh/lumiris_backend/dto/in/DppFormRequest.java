package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Corps d'un DPP, brouillon comme publication. Les contraintes ne portent que sur la forme
 * (longueurs, bornes, formats) et valent donc aussi pour un brouillon ; la complétude (nom,
 * catégorie, pays…) n'est exigée qu'à la publication, voir DppPublicationService.assertPublishable.
 * Un champ vide reste accepté : le wizard enregistre des brouillons partiels.
 */
public record DppFormRequest(
        @Size(max = 255) String productName,
        @Size(max = 2000) String productDescription,
        @Size(max = 100) String productCategory,
        @Size(max = 100) String originCountry,
        @Size(max = 30) List<@Size(max = 20) String> availableSizes,
        @Size(max = 30) List<@Size(max = 50) String> colors,

        @Size(max = 20) List<@NotNull @Valid MaterialRequest> materials,
        @Size(max = 30) List<@NotBlank @Size(max = 64) String> careInstructions,
        @Size(max = 2000) String careNotes,

        @Pattern(regexp = "^$|\\d{4}-\\d{2}-\\d{2}", message = "doit être une date au format AAAA-MM-JJ")
        String manufacturedAt,
        @Size(max = 100) String batchNumber,
        // GTIN-8, GTIN-12 (UPC-A), GTIN-13 (EAN-13) ou GTIN-14.
        @Pattern(regexp = "^$|\\d{8}|\\d{12,14}", message = "doit être un GTIN de 8, 12, 13 ou 14 chiffres")
        String gtin,
        @Size(max = 100) String sku,
        Boolean reachCompliant,

        // Mêmes bornes que le wizard (apps/client, étapes produit et éco).
        @Min(1) @Max(50_000) Integer weightGrams,
        @Min(0) @Max(100) Integer recycledPct,
        @Size(max = 2000) String warrantyDescription,
        @Min(0) @Max(1200) Integer warrantyMonths,
        Boolean isRepairable,
        @Size(max = 2000) String endOfLifeInstructions,

        @Min(1) Integer quantity,

        // Rattachement d'un certificat déjà présent dans la bibliothèque de l'artisan, en
        // alternative à l'upload d'un fichier neuf pour ce champ (voir DppDocumentService —
        // fournir les deux pour le même slot est rejeté avec un 400).
        UUID transactionCertLibraryId,
        UUID originCertLibraryId
) {}
