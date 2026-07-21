package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RepairRequestCreateRequest(
        @NotNull UUID repairerId,
        @NotBlank String dppPublicCode,
        String message
) {}
