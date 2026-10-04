package com.minoh.lumiris_backend.marketplace.shipping.controller;

import com.minoh.lumiris_backend.marketplace.shipping.service.CarrierTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingWebhookController {

    private final CarrierTrackingService trackingService;

    @PostMapping("/webhook")
    ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(name = "Sendcloud-Signature", required = false) String signature
    ) {
        trackingService.handle(payload, signature);
        return ResponseEntity.ok("ok");
    }
}
