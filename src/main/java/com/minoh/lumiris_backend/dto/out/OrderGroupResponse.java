package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;

import java.time.Instant;
import java.util.List;

// Vue groupée d'une commande = toutes les lignes d'un même PaymentIntent (le backend crée une
// ligne par article). Donne à l'écran de confirmation le montant EXACT débité (articles + livraison),
// au lieu du seul total d'une ligne. `status` = statut agrégé (PENDING tant qu'une ligne est PENDING).
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
    public static OrderGroupResponse from(String paymentIntentId, List<MarketplaceOrder> orders) {
        List<OrderResponse> lines = orders.stream().map(OrderResponse::from).toList();
        int items = orders.stream().mapToInt(MarketplaceOrder::getAmountTotalCents).sum();
        int shipping = orders.stream().mapToInt(MarketplaceOrder::getShippingCents).sum();
        String currency = orders.isEmpty() ? "EUR" : orders.get(0).getCurrency();
        // Statut agrégé : PENDING si au moins une ligne l'est ; sinon le statut commun (toutes payées/…).
        boolean anyPending = orders.stream().anyMatch(o -> o.getStatus().name().equals("PENDING"));
        String status = anyPending ? "PENDING"
                : orders.isEmpty() ? "PENDING" : orders.get(0).getStatus().name();
        String invoice = orders.stream()
                .map(MarketplaceOrder::getInvoiceNumber)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        Instant createdAt = orders.isEmpty() ? null : orders.get(0).getCreatedAt();
        return new OrderGroupResponse(paymentIntentId, lines, items, shipping,
                items + shipping, currency, status, invoice, createdAt);
    }
}
