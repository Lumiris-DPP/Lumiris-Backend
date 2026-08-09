package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

// Vue groupée d'une commande = toutes les lignes d'un même PaymentIntent (le backend crée une
// ligne par article). Donne à l'écran de confirmation le montant EXACT débité (articles +
// livraison de chaque atelier), au lieu du seul total d'une ligne. `status` = état le moins avancé
// des lignes : un panier multi-atelier n'est « expédié » que lorsque tous ses colis le sont.
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
        String invoice = orders.stream()
                .map(MarketplaceOrder::getInvoiceNumber)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        Instant createdAt = orders.isEmpty() ? null : orders.get(0).getCreatedAt();
        return new OrderGroupResponse(paymentIntentId, lines, items, shipping,
                items + shipping, currency, aggregateStatus(orders), invoice, createdAt);
    }

    // L'ordre de l'enum suit la progression du cycle : le minimum est donc l'étape la plus en
    // retard, celle qui décrit honnêtement l'avancement du panier entier.
    private static String aggregateStatus(List<MarketplaceOrder> orders) {
        return orders.stream()
                .map(MarketplaceOrder::getStatus)
                .min(Comparator.comparingInt(OrderStatus::ordinal))
                .orElse(OrderStatus.PENDING)
                .name();
    }
}
