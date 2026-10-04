package com.minoh.lumiris_backend.marketplace.catalog.dto.in;

import com.minoh.lumiris_backend.dto.in.HttpUrl;
import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 4000) String description,
        @Size(max = 100) String category,
        @Size(max = 100) String material,
        @Size(max = 100) String originCountry,
        @Min(0) int priceCents,
        @Size(max = 3) String currency,
        @NotNull @Min(0) Integer shippingCents,
        @Size(max = 2000) String returnPolicy,
        @NotNull @Min(0) @Max(90) Integer preparationDays,
        @NotNull @Min(0) @Max(30000) Integer weightGrams,
        @NotEmpty @Valid List<ProductVariantForm> variants,
        @Valid List<SizeMeasurementForm> sizeGuide,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String externalOrderUrl,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String photoUrl,
        UUID dppFormId,
        MarketplaceProductStatus status
) {}
