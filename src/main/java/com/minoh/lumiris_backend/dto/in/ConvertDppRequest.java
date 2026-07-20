package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConvertDppRequest(
        @Min(0) int priceCents,
        @Size(max = 3) String currency,
        @Size(max = 4000) String description,
        @Size(max = 100) String material,
        Integer stock,
        @Min(0) Integer shippingCents,
        @Size(max = 2000) String returnPolicy,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String externalOrderUrl,
        @Size(max = 2048) @Pattern(regexp = HttpUrl.REGEX, message = HttpUrl.MESSAGE) String photoUrl,
        MarketplaceProductStatus status
) {}
