package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceUpdateRequest(
        @NotNull Boolean emailEnabled,
        @NotNull Boolean pushEnabled
) {
}
