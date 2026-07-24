package com.minoh.lumiris_backend.entity;

import java.util.Set;

/**
 * Niveau d'accès accordé par un QR code
 */
public enum DppAccessLevel {
    PUBLIC(Set.of(DppDocumentVisibility.PUBLIC_USERS)),

    CIRCULAR_OPERATORS(Set.of(
            DppDocumentVisibility.PUBLIC_USERS,
            DppDocumentVisibility.CIRCULAR_OPERATORS)),

    AUTHORITIES(Set.of(
            DppDocumentVisibility.PUBLIC_USERS,
            DppDocumentVisibility.CIRCULAR_OPERATORS,
            DppDocumentVisibility.AUTHORITIES));

    private final Set<DppDocumentVisibility> visibilities;

    DppAccessLevel(Set<DppDocumentVisibility> visibilities) {
        this.visibilities = visibilities;
    }

    public Set<DppDocumentVisibility> visibilities() {
        return visibilities;
    }

    /** PUBLIC n'est pas un grant : il n'a ni token ni ligne en base. */
    public boolean isGrantable() {
        return this != PUBLIC;
    }
}
