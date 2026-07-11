package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MaterialRequest(
        @Size(max = 100) String fiber,
        @Min(0) @Max(100) Integer percentage,
        @Size(max = 100) String originCountry
) {}
