package com.minoh.lumiris_backend.dto.in;

import java.util.List;
import java.util.UUID;

public record DppFormRequest(
        String productName,
        String productDescription,
        String productCategory,
        String originCountry,
        List<String> availableSizes,
        List<String> colors,

        List<MaterialRequest> materials,
        List<String> careInstructions,
        String careNotes,

        String manufacturedAt,
        String batchNumber,
        String gtin,
        String sku,
        Boolean reachCompliant,

        Integer weightGrams,
        Integer recycledPct,
        String warrantyDescription,
        Integer warrantyMonths,
        Boolean isRepairable,
        String endOfLifeInstructions,

        Integer quantity,

        // Rattachement d'un certificat déjà présent dans la bibliothèque de l'artisan, en
        // alternative à l'upload d'un fichier neuf pour ce champ (voir DppFormService —
        // fournir les deux pour le même slot est rejeté avec un 400).
        UUID transactionCertLibraryId,
        UUID originCertLibraryId
) {}
