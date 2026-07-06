package com.minoh.lumiris_backend.dto.out;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String code,
        String message,
        Instant timestamp,
        Map<String, String> errors
) {
    public ErrorResponse(int status, String message) {
        this(status, null, message, Instant.now(), null);
    }

    public ErrorResponse(int status, String message, Map<String, String> errors) {
        this(status, null, message, Instant.now(), errors);
    }

    public ErrorResponse(int status, String code, String message) {
        this(status, code, message, Instant.now(), null);
    }
}