package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;

import java.time.Instant;
import java.util.UUID;

// LUMIRIS · Ligne de l'historique des ventes (côté vendeur). Montants en centimes.
// released = fonds déjà reversés au vendeur (Transfer créé) ; sinon retenus par la plateforme (escrow).
public record SellerSaleResponse(
        UUID id,
        String productName,
        int amountTotalCents,
        int commissionCents,
        int netCents,
        String currency,
        String status,
        boolean released,
        Instant releasedAt,
        Instant createdAt,
        String invoiceNumber
) {
    public static SellerSaleResponse from(MarketplaceOrder o) {
        return new SellerSaleResponse(
                o.getId(),
                o.getProduct() != null ? o.getProduct().getName() : null,
                o.getAmountTotalCents(),
                o.getCommissionCents(),
                o.getNetCents(),
                o.getCurrency(),
                o.getStatus().name(),
                o.getStripeTransferId() != null,
                o.getReleasedAt(),
                o.getCreatedAt(),
                o.getInvoiceNumber()
        );
    }
}
