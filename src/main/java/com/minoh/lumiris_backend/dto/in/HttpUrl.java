package com.minoh.lumiris_backend.dto.in;

// Regroupe la validation des liens HTTP et HTTPS.
public final class HttpUrl {

    public static final String REGEX = "^(https?://[^\\s\"'<>`\\\\]+)?$";
    public static final String MESSAGE = "Doit être une URL http(s) sans caractère de contrôle ou HTML";

    // Empêche la création d'une instance de ces constantes.
    private HttpUrl() {}
}
