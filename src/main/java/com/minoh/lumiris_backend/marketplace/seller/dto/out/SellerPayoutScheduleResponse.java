package com.minoh.lumiris_backend.marketplace.seller.dto.out;

import java.util.List;

public record SellerPayoutScheduleResponse(
        long scheduledCents,
        long releasedCents,
        long onHoldCents,
        String currency,
        List<SellerPayoutEntryResponse> entries
) {}
