package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.DppAccessLevel;

/**
 * @param accessLevel périmètre d'accès
 *
 */
public record DppFormPublicResponse(
        DppFormResponse dpp,
        IrisScoreResponse irisScore,
        String artisanSlug,
        DppAccessLevel accessLevel
) {}
