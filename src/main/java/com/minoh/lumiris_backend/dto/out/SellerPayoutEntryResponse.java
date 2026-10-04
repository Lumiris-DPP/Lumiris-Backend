package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.PayoutExpectation;
import java.time.Instant;
import java.util.UUID;

// Présente le versement attendu pour une commande.
public record SellerPayoutEntryResponse(
        UUID orderId,
        String productName,
        String variantLabel,
        String buyerName,
        int netCents,
        String currency,
        Instant expectedAt,
        PayoutExpectation expectation,
        OrderStatus status
) {}
