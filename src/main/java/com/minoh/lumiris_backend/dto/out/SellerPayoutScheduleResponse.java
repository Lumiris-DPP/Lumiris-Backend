package com.minoh.lumiris_backend.dto.out;

import java.util.List;

// Présente les montants et l'échéancier des versements.
public record SellerPayoutScheduleResponse(
        long scheduledCents,
        long releasedCents,
        long onHoldCents,
        String currency,
        List<SellerPayoutEntryResponse> entries
) {}
