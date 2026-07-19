package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Mise à jour (remplacement complet) d'un produit du catalogue artisan.
public record UpdateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 4000) String description,
        @Size(max = 100) String category,
        @Size(max = 100) String material,
        @Size(max = 100) String originCountry,
        @Min(0) int priceCents,
        @Size(max = 3) String currency,
        @Min(0) int stock,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String externalOrderUrl,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String photoUrl,
        UUID dppFormId,
        MarketplaceProductStatus status
) implements ProductForm {}
