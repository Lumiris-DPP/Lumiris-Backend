package com.minoh.lumiris_backend.dto.in;

/** Partage les contraintes des URL HTTP sans caractères de contrôle ou HTML. */
public final class HttpUrl {

    public static final String REGEX = "^(https?://[^\\s\"'<>`\\\\]+)?$";
    public static final String MESSAGE = "Doit être une URL http(s) sans caractère de contrôle ou HTML";

    /** Empêche l’instanciation de ce regroupement de contraintes. */
    private HttpUrl() {}
}
