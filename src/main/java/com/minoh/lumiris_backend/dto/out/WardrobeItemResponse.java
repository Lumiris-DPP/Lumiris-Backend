package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.WardrobeItem;

import java.time.Instant;
import java.util.UUID;

// Une pièce de la Garde-Robe de l'acheteur (achetée en direct) : passeport + facture + garantie.
public record WardrobeItemResponse(
        UUID id,
        UUID dppFormId,
        String dppPublicCode,
        String productName,
        String warrantyDescription,
        String invoiceNumber,
        Instant acquiredAt
) {
    public static WardrobeItemResponse from(WardrobeItem item) {
        var dpp = item.getDppForm();
        return new WardrobeItemResponse(
                item.getId(),
                dpp != null ? dpp.getId() : null,
                dpp != null ? dpp.getPublicCode() : null,
                dpp != null ? dpp.getProductName() : null,
                item.getWarrantyDescription(),
                item.getInvoiceNumber(),
                item.getAcquiredAt()
        );
    }
}
