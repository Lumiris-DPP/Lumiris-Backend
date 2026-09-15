package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record RepairQuoteRequest(
        @Positive long amountCents,
        @NotBlank String description
) {}
