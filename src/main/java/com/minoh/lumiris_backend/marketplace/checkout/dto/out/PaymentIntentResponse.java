package com.minoh.lumiris_backend.marketplace.checkout.dto.out;

import java.util.List;

public record PaymentIntentResponse(
        String clientSecret,
        String publishableKey,
        int amountTotalCents,
        int itemsTotalCents,
        int shippingTotalCents,
        int commissionCents,
        List<Shipment> shipments
) {

    public record Shipment(String sellerName, int itemCount, int shippingCents, int preparationDays) {}
}
