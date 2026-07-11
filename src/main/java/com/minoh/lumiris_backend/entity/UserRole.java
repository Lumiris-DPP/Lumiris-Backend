package com.minoh.lumiris_backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    ARTISAN, CONSUMER, ADMIN, REPAIRER;

    // Roles a user may pick for themselves at sign-up. ADMIN/REPAIRER are provisioned internally only.
    public boolean isSelfAssignable() {
        return this == ARTISAN || this == CONSUMER;
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static UserRole fromJson(String value) {
        return valueOf(value.toUpperCase());
    }
}
