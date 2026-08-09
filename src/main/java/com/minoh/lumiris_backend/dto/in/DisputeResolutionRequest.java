package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Arbitrage d'un litige par la plateforme. `refundCents` renseigné ⇒ tranché en faveur de
// l'acheteur (remboursement de ce montant) ; absent ⇒ clos sans remboursement.
public record DisputeResolutionRequest(
        @NotBlank @Size(max = 2000) String resolution,
        @Positive Integer refundCents
) {}
