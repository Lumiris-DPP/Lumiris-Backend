package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;

import java.time.Instant;
import java.util.UUID;

// Vue d'une commande d'achat direct (acheteur ou vendeur). commissionCents = part plateforme.
public record OrderResponse(
        UUID id,
        String productName,
        int amountTotalCents,
        int shippingCents,
        int commissionCents,
        String currency,
        String status,
        String invoiceNumber,
        String paymentIntentId,
        Instant createdAt
) {
    public static OrderResponse from(MarketplaceOrder o) {
        return new OrderResponse(
                o.getId(),
                o.getProduct() != null ? o.getProduct().getName() : null,
                o.getAmountTotalCents(),
                o.getShippingCents(),
                o.getCommissionCents(),
                o.getCurrency(),
                o.getStatus().name(),
                o.getInvoiceNumber(),
                o.getStripePaymentIntentId(),
                o.getCreatedAt()
        );
    }
}
