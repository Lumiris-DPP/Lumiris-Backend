package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

// Suivi de commande côté acheteur : la ligne, son adresse de livraison, la piste des transitions
// (timeline) et le dossier de litige s'il existe.
public record OrderDetailResponse(
        OrderResponse order,
        ShippingAddressResponse shipTo,
        String returnReason,
        String returnDecisionNote,
        String disputeReason,
        String disputeResolution,
        List<OrderEventResponse> timeline
) {
    public static OrderDetailResponse from(MarketplaceOrder o, List<OrderEvent> events,
                                           Function<UUID, String> presign) {
        return new OrderDetailResponse(
                OrderResponse.from(o),
                ShippingAddressResponse.from(o),
                o.getReturnReason(),
                o.getReturnDecisionNote(),
                o.getDisputeReason(),
                o.getDisputeResolution(),
                events.stream().map(e -> OrderEventResponse.from(e, presign)).toList()
        );
    }
}
