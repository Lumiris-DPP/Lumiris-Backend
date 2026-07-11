package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minoh.lumiris_backend.entity.User;
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
) {
    public static UserResponse from(User user) {
        String artisanId = null;
        if (user.getRole() == UserRole.ARTISAN && user.getArtisanProfile() != null) {
            artisanId = user.getArtisanProfile().getId().toString();
        }
        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getRole(),
                user.getName(),
                user.getAvatarUrl(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                user.getLastSeenAt() != null ? user.getLastSeenAt().toString() : null,
                artisanId
        );
    }
}
