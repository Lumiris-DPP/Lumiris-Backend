package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import java.time.Instant;
import java.util.UUID;

// Présente une commande à son acheteur.
public record OrderResponse(
        UUID id,
        String productName,
        String variantLabel,
        String productPhotoUrl,
        String sellerName,
        int quantity,
        int amountTotalCents,
        int shippingCents,
        int commissionCents,
        int refundedCents,
        String currency,
        String status,
        String disputeStatus,
        String invoiceNumber,
        String paymentIntentId,
        String carrier,
        String trackingNumber,
        String trackingUrl,
        String trackingStatus,
        String trackingStatusLabel,
        Instant trackingUpdatedAt,
        Instant shipDueAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant returnDeadline,
        Instant createdAt,
        boolean canConfirmDelivery,
        boolean canRequestReturn,
        boolean canOpenDispute,
        boolean canCancel
) {

    // Prépare la réponse avec les informations de la commande acheteur.
    public static OrderResponse from(MarketplaceOrder o) {
        OrderStatus status = o.getStatus();
        boolean returnWindowOpen = o.getReturnDeadline() == null || Instant.now().isBefore(o.getReturnDeadline());
        return new OrderResponse(
                o.getId(),
                o.getProduct() != null ? o.getProduct().getName() : null,
                o.getVariantLabel(),
                o.getProduct() != null ? o.getProduct().getPhotoUrl() : null,
                o.getSeller() != null && o.getSeller().getArtisanProfile() != null
                        ? o.getSeller().getArtisanProfile().getDisplayName() : null,
                o.getQuantity(),
                o.getAmountTotalCents(),
                o.getShippingCents(),
                o.getCommissionCents(),
                o.getRefundedCents(),
                o.getCurrency(),
                status.name(),
                o.getDisputeStatus().name(),
                o.getInvoiceNumber(),
                o.getStripePaymentIntentId(),
                o.getCarrier(),
                o.getTrackingNumber(),
                o.getTrackingUrl(),
                o.getTrackingStatus() != null ? o.getTrackingStatus().name() : null,
                o.getTrackingStatusLabel(),
                o.getTrackingUpdatedAt(),
                o.getShipDueAt(),
                o.getShippedAt(),
                o.getDeliveredAt(),
                o.getReturnDeadline(),
                o.getCreatedAt(),
                status == OrderStatus.SHIPPED,
                status.allowsReturnRequest() && returnWindowOpen,
                status != OrderStatus.PENDING && o.getDisputeStatus() == DisputeStatus.NONE,
                status == OrderStatus.PAID
        );
    }
}
