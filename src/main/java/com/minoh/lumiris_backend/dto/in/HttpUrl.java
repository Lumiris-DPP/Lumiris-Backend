package com.minoh.lumiris_backend.dto.in;

final class HttpUrl {

    static final String REGEX = "^(https?://[^\\s\"'<>`\\\\]+)?$";
    static final String MESSAGE = "Doit être une URL http(s) sans caractère de contrôle ou HTML";

    private HttpUrl() {}
}
