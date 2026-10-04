package com.minoh.lumiris_backend.dto.in;

public final class HttpUrl {

    public static final String REGEX = "^(https?://[^\\s\"'<>`\\\\]+)?$";
    public static final String MESSAGE = "Doit être une URL http(s) sans caractère de contrôle ou HTML";

    private HttpUrl() {}
}
