package com.minoh.lumiris_backend.marketplace.shipping.service;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.marketplace.order.service.OrderLifecycleService;
import com.minoh.lumiris_backend.marketplace.shipping.dto.CarrierEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarrierTrackingService {

    private static final Logger log = LoggerFactory.getLogger(CarrierTrackingService.class);

    private final ShippingProvider provider;
    private final MarketplaceOrderRepository orderRepository;
    private final OrderLifecycleService lifecycleService;

    @Transactional
    public void handle(String payload, String signature) {
        Optional<CarrierEvent> event = provider.readWebhook(payload, signature);
        if (event.isEmpty()) {
            return;
        }
        CarrierEvent carrierEvent = event.get();

        MarketplaceOrder order = orderRepository.findByCarrierParcelId(carrierEvent.parcelId()).orElse(null);
        if (order == null) {
            log.debug("Événement transporteur ignoré : colis {} inconnu", carrierEvent.parcelId());
            return;
        }
        lifecycleService.applyTrackingUpdate(order, carrierEvent.status(), carrierEvent.label(),
                carrierEvent.trackingNumber(), carrierEvent.trackingUrl());
    }
}
