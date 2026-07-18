package com.minoh.lumiris_backend.dto.out;

public record AuthResponse(
        String token,
        String refreshToken,
        UserResponse user
) {}
