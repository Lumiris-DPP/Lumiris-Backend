package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Décrit un remboursement et son identifiant optionnel à conserver pour les reprises. */
public record RefundRequest(
        @Positive Integer amountCents,
        @Size(max = 2000) String reason,
        UUID operationId
) {

    /** Décrit un remboursement et son identifiant optionnel à conserver pour les reprises. */
    public RefundRequest(Integer amountCents, String reason) {
        this(amountCents, reason, null);
    }
}
