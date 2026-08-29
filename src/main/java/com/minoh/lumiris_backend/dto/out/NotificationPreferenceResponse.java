package com.minoh.lumiris_backend.dto.out;

public record NotificationPreferenceResponse(
        String category,
        boolean emailEnabled,
        boolean pushEnabled
) {
}
