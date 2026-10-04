package com.minoh.lumiris_backend.marketplace.catalog.dto.in;

import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;

import java.util.List;
import java.util.UUID;

public interface ProductForm {

    String name();

    String description();

    String category();

    String material();

    String originCountry();

    int priceCents();

    String currency();

    Integer shippingCents();

    String returnPolicy();

    Integer preparationDays();

    Integer weightGrams();

    List<ProductVariantForm> variants();

    List<SizeMeasurementForm> sizeGuide();

    String externalOrderUrl();

    String photoUrl();

    UUID dppFormId();

    MarketplaceProductStatus status();
}
