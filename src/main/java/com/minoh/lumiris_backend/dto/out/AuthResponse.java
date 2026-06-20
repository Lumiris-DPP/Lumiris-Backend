package com.minoh.lumiris_backend.dto.out;

public record AuthResponse(
        String token,
        UserResponse user
) {}
