package com.minoh.lumiris_backend.dto.out;

import java.util.List;

// Échéancier de trésorerie vendeur. Les totaux sont calculés ici et jamais re-sommés côté client :
// pour un indépendant, savoir quand il est payé vaut plus que le total.
public record SellerPayoutScheduleResponse(
        long scheduledCents,
        long releasedCents,
        long onHoldCents,
        String currency,
        List<SellerPayoutEntryResponse> entries
) {}
