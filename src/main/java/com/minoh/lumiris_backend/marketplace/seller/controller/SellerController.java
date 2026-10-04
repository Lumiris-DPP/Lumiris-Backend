package com.minoh.lumiris_backend.marketplace.seller.controller;

import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.dto.out.CheckoutResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerEarningsResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerPayoutScheduleResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerSaleResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerStatsResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerStatusResponse;
import com.minoh.lumiris_backend.marketplace.seller.service.SellerConnectService;
import com.minoh.lumiris_backend.marketplace.seller.service.SellerStatsService;
import com.minoh.lumiris_backend.marketplace.shipping.dto.in.ShipFromAddressRequest;
import com.minoh.lumiris_backend.marketplace.shipping.dto.out.ShipFromAddressResponse;
import com.minoh.lumiris_backend.marketplace.shipping.service.ShipFromAddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerConnectService sellerConnectService;
    private final SellerStatsService sellerStatsService;
    private final ShipFromAddressService shipFromAddressService;

    @PostMapping("/onboarding")
    ResponseEntity<CheckoutResponse> onboarding(@CurrentUserEmail String email) {
        return ResponseEntity.ok(new CheckoutResponse(sellerConnectService.startOnboarding(email)));
    }

    @GetMapping("/status")
    ResponseEntity<SellerStatusResponse> status(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerConnectService.getStatus(email));
    }

    @GetMapping("/stats")
    ResponseEntity<SellerStatsResponse> stats(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getStats(email));
    }

    @GetMapping("/sales")
    ResponseEntity<List<SellerSaleResponse>> sales(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getSales(email));
    }

    @GetMapping("/earnings")
    ResponseEntity<SellerEarningsResponse> earnings(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getEarnings(email));
    }

    @GetMapping("/payouts")
    ResponseEntity<SellerPayoutScheduleResponse> payouts(@CurrentUserEmail String email) {
        return ResponseEntity.ok(sellerStatsService.getPayoutSchedule(email));
    }

    @GetMapping("/shipping-address")
    ResponseEntity<ShipFromAddressResponse> shipFromAddress(@CurrentUserEmail String email) {
        return ResponseEntity.ok(shipFromAddressService.get(email));
    }

    @PutMapping("/shipping-address")
    ResponseEntity<ShipFromAddressResponse> updateShipFromAddress(
            @Valid @RequestBody ShipFromAddressRequest request,
            @CurrentUserEmail String email) {
        return ResponseEntity.ok(shipFromAddressService.update(email, request));
    }
}
