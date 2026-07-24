package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.DppAccessLevel;

/**
 * Un des trois QR d'un passeport publié.
 *
 * @param token {@code null} pour PUBLIC, dont le QR ne porte que le code public.
 */
public record DppAccessTokenResponse(
        DppAccessLevel accessLevel,
        String token
) {}
