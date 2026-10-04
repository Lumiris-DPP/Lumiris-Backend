package com.minoh.lumiris_backend.marketplace.order.dto.out;

import java.util.UUID;

public record OrderAttachmentResponse(
        UUID id,
        String filename,
        String contentType,
        String url
) {}
