package com.minoh.lumiris_backend.dto.out;

import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;

import java.util.UUID;

public record DppVerificationResponse(
        UUID id,
        boolean verified,
        String blockchainHash,
        String recomputedHash,
        String blockchainTxHash,
        BlockchainAnchorStatus anchorStatus,
        String message
) {}
