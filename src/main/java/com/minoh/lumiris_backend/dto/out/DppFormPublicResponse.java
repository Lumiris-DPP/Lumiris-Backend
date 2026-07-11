package com.minoh.lumiris_backend.dto.out;

public record DppFormPublicResponse(
        DppFormResponse dpp,
        IrisScoreResponse irisScore
) {}
