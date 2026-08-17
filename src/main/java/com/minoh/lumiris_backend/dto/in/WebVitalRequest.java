package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WebVitalRequest(
        @NotBlank @Pattern(regexp = "CLS|FCP|FID|INP|LCP|TTFB") String name,
        @NotNull Double value,
        @NotBlank @Pattern(regexp = "good|needs-improvement|poor") String rating,
        @NotBlank @Pattern(regexp = "admin|site|client|mobile") String app,
        String sessionId,
        String route,
        String navigationType,
        Long timestamp
) {}
