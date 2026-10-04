package com.minoh.lumiris_backend.marketplace.shipping.service;

import com.minoh.lumiris_backend.marketplace.shipping.dto.CarrierEvent;
import com.minoh.lumiris_backend.marketplace.shipping.dto.ParcelRequest;
import com.minoh.lumiris_backend.marketplace.shipping.dto.ShippingLabel;
import java.util.Optional;

public interface ShippingProvider {

    String name();

    boolean configured();

    ShippingLabel createLabel(ParcelRequest request);

    Optional<CarrierEvent> readWebhook(String payload, String signature);
}
