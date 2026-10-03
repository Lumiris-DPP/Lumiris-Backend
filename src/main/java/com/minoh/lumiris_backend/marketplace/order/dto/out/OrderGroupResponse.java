package com.minoh.lumiris_backend.marketplace.order.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Décrit les données de OrderGroupResponse pour les commandes. */
public record OrderGroupResponse(
        String paymentIntentId,
        List<OrderResponse> lines,
        int itemsTotalCents,
        int shippingCents,
        int amountChargedCents,
        String currency,
        String status,
        String invoiceNumber,
        Instant createdAt
) {

    /** Construit la réponse à partir des données persistantes de la commande. */
    public static OrderGroupResponse from(String paymentIntentId, List<MarketplaceOrder> orders) {
        List<OrderResponse> lines = orders.stream().map(OrderResponse::from).toList();
        int items = orders.stream().mapToInt(MarketplaceOrder::getAmountTotalCents).sum();
        int shipping = orders.stream().mapToInt(MarketplaceOrder::getShippingCents).sum();
        String currency = orders.isEmpty() ? "EUR" : orders.get(0).getCurrency();
        String invoice = orders.stream()
                .map(MarketplaceOrder::getInvoiceNumber)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        Instant createdAt = orders.isEmpty() ? null : orders.get(0).getCreatedAt();
        return new OrderGroupResponse(paymentIntentId, lines, items, shipping,
                items + shipping, currency, aggregateStatus(orders), invoice, createdAt);
    }

    /** Retient l’état le moins avancé des lignes du paiement. */
    private static String aggregateStatus(List<MarketplaceOrder> orders) {
        return orders.stream()
                .map(MarketplaceOrder::getStatus)
                .min(Comparator.comparingInt(OrderStatus::ordinal))
                .orElse(OrderStatus.PENDING)
                .name();
    }
}
