package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConvertDppRequest(
        @Min(0) int priceCents,
        @Size(max = 3) String currency,
        @Size(max = 4000) String description,
        @Size(max = 100) String material,
        @Min(0) Integer stock,
        @Valid List<ProductVariantForm> variants,
        @Valid List<SizeMeasurementForm> sizeGuide,
        @Min(0) Integer shippingCents,
        @Size(max = 2000) String returnPolicy,
        @Min(0) @Max(90) Integer preparationDays,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String externalOrderUrl,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String photoUrl,
        MarketplaceProductStatus status
) {}
