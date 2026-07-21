package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RepairerReviewRequest(
        @Min(1) @Max(5) int rating,
        String comment,
        @NotBlank String reviewerName
) {}
