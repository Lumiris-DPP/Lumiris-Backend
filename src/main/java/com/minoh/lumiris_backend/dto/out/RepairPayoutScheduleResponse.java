package com.minoh.lumiris_backend.dto.out;

import java.util.List;

public record RepairPayoutScheduleResponse(
        long scheduledCents,
        long releasedCents,
        long onHoldCents,
        String currency,
        List<RepairPayoutEntryResponse> entries
) {}
