package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.PayoutExpectation;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;

import java.time.Instant;
import java.util.UUID;

// Une échéance de versement de devis réparation — même forme que SellerPayoutEntryResponse (vente
// directe artisan), typée pour RepairRequestStatus.
public record RepairPayoutEntryResponse(
        UUID requestId,
        String productName,
        String consumerName,
        int netCents,
        String currency,
        Instant expectedAt,
        PayoutExpectation expectation,
        RepairRequestStatus status
) {}
