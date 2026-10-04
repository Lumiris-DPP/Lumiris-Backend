package com.minoh.lumiris_backend.marketplace.catalog.dto.out;

import com.minoh.lumiris_backend.config.MarketplaceProperties;

public record PaymentOptionsResponse(
        boolean installmentsEnabled,
        int installmentCount,
        int installmentMinCents
) {

    public static PaymentOptionsResponse from(MarketplaceProperties properties) {
        int count = properties.getInstallmentCount();
        return new PaymentOptionsResponse(
                count > 1,
                count,
                properties.getInstallmentMinCents());
    }
}
