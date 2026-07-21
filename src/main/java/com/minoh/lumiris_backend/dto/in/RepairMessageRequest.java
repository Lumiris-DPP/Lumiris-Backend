package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;

public record RepairMessageRequest(
        @NotBlank String body
) {}
