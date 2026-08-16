package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderEvent;
import com.minoh.lumiris_backend.entity.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

// Vue vendeur d'une commande (tableau de bord ATELIER) : ce qu'il doit expédier, à qui, et où en
// est l'argent. `released` = fonds déjà reversés ; sinon retenus par la plateforme (escrow).
// Les drapeaux `can*` traduisent la machine à états : l'UI n'affiche que des actions acceptées.
public record SellerOrderResponse(
        UUID id,
        String productName,
        String variantLabel,
        String productPhotoUrl,
        String buyerName,
        int quantity,
        int amountTotalCents,
        int shippingCents,
        int commissionCents,
        int netCents,
        int refundedCents,
        String currency,
        String status,
        String disputeStatus,
        String invoiceNumber,
        String carrier,
        String trackingNumber,
        String trackingUrl,
        ShippingAddressResponse shipTo,
        String returnReason,
        String disputeReason,
        boolean released,
        Instant releasedAt,
        Instant shipDueAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant returnRequestedAt,
        Instant createdAt,
        boolean canShip,
        boolean canDecideReturn,
        boolean canMarkReturnReceived,
        boolean canRefund,
        boolean canCancel,
        List<OrderEventResponse> timeline
) {
    public static SellerOrderResponse from(MarketplaceOrder o, List<OrderEvent> events,
                                          Function<UUID, String> presign) {
        OrderStatus status = o.getStatus();
        int charged = o.getAmountTotalCents() + o.getShippingCents();
        return new SellerOrderResponse(
                o.getId(),
                o.getProduct() != null ? o.getProduct().getName() : null,
                o.getVariantLabel(),
                o.getProduct() != null ? o.getProduct().getPhotoUrl() : null,
                o.getBuyer() != null ? o.getBuyer().getName() : null,
                o.getQuantity(),
                o.getAmountTotalCents(),
                o.getShippingCents(),
                o.getCommissionCents(),
                o.getNetCents(),
                o.getRefundedCents(),
                o.getCurrency(),
                status.name(),
                o.getDisputeStatus().name(),
                o.getInvoiceNumber(),
                o.getCarrier(),
                o.getTrackingNumber(),
                o.getTrackingUrl(),
                ShippingAddressResponse.from(o),
                o.getReturnReason(),
                o.getDisputeReason(),
                o.getStripeTransferId() != null,
                o.getReleasedAt(),
                o.getShipDueAt(),
                o.getShippedAt(),
                o.getDeliveredAt(),
                o.getReturnRequestedAt(),
                o.getCreatedAt(),
                status == OrderStatus.PAID,
                status == OrderStatus.RETURN_REQUESTED,
                status == OrderStatus.RETURN_APPROVED,
                status != OrderStatus.PENDING && o.getRefundedCents() < charged,
                status == OrderStatus.PAID,
                events.stream().map(e -> OrderEventResponse.from(e, presign)).toList()
        );
    }
}
