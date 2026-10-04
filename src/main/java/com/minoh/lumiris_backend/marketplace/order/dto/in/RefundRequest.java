package com.minoh.lumiris_backend.marketplace.order.dto.in;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// Porte le montant et la référence d'un remboursement.
public record RefundRequest(
        @Positive Integer amountCents,
        @Size(max = 2000) String reason,
        UUID operationId
) {

    // Prépare un remboursement sans référence d'opération fournie.
    public RefundRequest(Integer amountCents, String reason) {
        this(amountCents, reason, null);
    }
}
