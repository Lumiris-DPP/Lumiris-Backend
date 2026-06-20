package com.minoh.lumiris_backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
    ARTISAN, CONSUMER, ADMIN, REPAIRER;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static UserRole fromJson(String value) {
        return valueOf(value.toUpperCase());
    }
}
