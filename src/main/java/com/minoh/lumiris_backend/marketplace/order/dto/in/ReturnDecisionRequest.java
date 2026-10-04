package com.minoh.lumiris_backend.marketplace.order.dto.in;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReturnDecisionRequest(
        boolean accepted,
        @Size(max = 2000) String note,
        List<UUID> fileIds
) {

    public List<UUID> attachments() {
        return fileIds == null ? List.of() : fileIds;
    }
}
