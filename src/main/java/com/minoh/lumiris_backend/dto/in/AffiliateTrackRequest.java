package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Clic vers un lien d'affiliation externe (endpoint public non authentifié). productId
// optionnel (une suggestion sur DPP scanné peut ne pas être rattachée à un produit du
// catalogue). Champs bornés + URL http(s) : ces valeurs finissent dans la piste d'audit
// et peuvent être rendues dans un outil interne — on limite la surface d'injection/abus.
public record AffiliateTrackRequest(
        @NotBlank @Size(max = 50) String source,
        UUID productId,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String targetUrl,
        @Size(max = 8) String dppPublicCode,
        @Size(max = 2048) String referrer
) {}
