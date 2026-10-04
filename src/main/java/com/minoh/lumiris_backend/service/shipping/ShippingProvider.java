package com.minoh.lumiris_backend.service.shipping;

import com.minoh.lumiris_backend.service.shipping.CarrierEvent;
import com.minoh.lumiris_backend.service.shipping.ParcelRequest;
import com.minoh.lumiris_backend.service.shipping.ShippingLabel;
import java.util.Optional;

public interface ShippingProvider {

    String name();

    boolean configured();

    ShippingLabel createLabel(ParcelRequest request);

    Optional<CarrierEvent> readWebhook(String payload, String signature);
}
