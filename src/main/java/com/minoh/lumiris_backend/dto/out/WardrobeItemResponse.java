package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.DppCareInstruction;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.WardrobeItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Une pièce de la Garde-Robe de l'acheteur : passeport + facture + garantie, et ce qu'il y a à
// faire pour elle. Les symboles d'entretien et l'échéance de garantie sont servis ici parce que
// la Garde-Robe est le seul écran qui parle de la pièce APRÈS la livraison — la période la plus
// longue de la relation, et jusqu'ici la seule où l'app n'avait rien à dire.
public record WardrobeItemResponse(
        UUID id,
        UUID dppFormId,
        String dppPublicCode,
        String productName,
        String variantLabel,
        String warrantyDescription,
        Instant warrantyUntil,
        List<String> careInstructions,
        String careNotes,
        String invoiceNumber,
        Instant acquiredAt
) {
    public static WardrobeItemResponse from(WardrobeItem item) {
        DppForm dpp = item.getDppForm();
        return new WardrobeItemResponse(
                item.getId(),
                dpp != null ? dpp.getId() : null,
                dpp != null ? dpp.getPublicCode() : null,
                dpp != null ? dpp.getProductName() : null,
                item.getOrder() != null ? item.getOrder().getVariantLabel() : null,
                item.getWarrantyDescription(),
                item.getWarrantyUntil(),
                dpp != null
                        ? dpp.getCareInstructions().stream().map(DppCareInstruction::getCareCode).toList()
                        : List.of(),
                dpp != null ? dpp.getCareNotes() : null,
                item.getInvoiceNumber(),
                item.getAcquiredAt()
        );
    }
}
