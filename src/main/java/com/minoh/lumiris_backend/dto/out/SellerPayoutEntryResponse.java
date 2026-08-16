package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.PayoutExpectation;

import java.time.Instant;
import java.util.UUID;

// Une échéance de versement. expectedAt est nul quand aucune date honnête n'existe (versement déjà
// dû et rejoué par le balayage, ou suspendu par un litige / un retour).
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
