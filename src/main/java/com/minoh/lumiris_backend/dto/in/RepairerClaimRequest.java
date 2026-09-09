package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RepairerClaimRequest(@NotNull UUID token) {}
