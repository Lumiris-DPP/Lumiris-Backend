package com.minoh.lumiris_backend.marketplace.order.dto.out;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

// Présente une commande et son historique.
public record OrderDetailResponse(
        OrderResponse order,
        ShippingAddressResponse shipTo,
        String returnReason,
        String returnDecisionNote,
        String disputeReason,
        String disputeResolution,
        List<OrderEventResponse> timeline
) {

    // Prépare la réponse avec les informations de la commande et son historique.
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
