package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minoh.lumiris_backend.entity.UserRole;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        String id,
        String email,
        UserRole role,
        String name,
        String avatar,
        String createdAt,
        String lastSeenAt,
        String artisanId
) {}
