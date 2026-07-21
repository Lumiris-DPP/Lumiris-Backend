package com.minoh.lumiris_backend.entity;

public enum LegalCategory {
    SOLO(0), COMPANY(1), ASSOCIATION(2);

    private final int code;

    LegalCategory(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static LegalCategory fromCode(int code) {
        for (LegalCategory c : values()) {
            if (c.code == code) return c;
        }
        throw new IllegalArgumentException("Unknown legal category code: " + code);
    }
}
