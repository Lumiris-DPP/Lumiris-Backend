package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Remboursement partiel ou total. `amountCents` absent ⇒ remboursement du solde intégral.
public record RefundRequest(
        @Positive Integer amountCents,
        @Size(max = 2000) String reason
) {}
