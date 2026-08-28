package com.minoh.lumiris_backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum WardrobeItemKind {
    LUMIRIS_PASSPORT("lumiris-passport"),
    EXTERNAL_DPP("external-dpp"),
    PUBLIC_DPP("public-dpp"),
    MANUAL("manual");

    private final String apiValue;

    WardrobeItemKind(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    @JsonCreator
    public static WardrobeItemKind fromApiValue(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.apiValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type de pièce inconnu : " + value));
    }
}

