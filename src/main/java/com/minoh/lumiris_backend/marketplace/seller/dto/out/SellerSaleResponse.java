package com.minoh.lumiris_backend.marketplace.seller.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import java.time.Instant;
import java.util.UUID;

// Présente une vente de l'atelier connecté.
public record SellerSaleResponse(
        UUID id,
        String productName,
        String variantLabel,
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

    // Prépare la réponse avec les informations de la vente.
    public static SellerSaleResponse from(MarketplaceOrder o) {
        return new SellerSaleResponse(
                o.getId(),
                o.getProduct() != null ? o.getProduct().getName() : null,
                o.getVariantLabel(),
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
