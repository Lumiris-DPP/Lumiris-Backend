package com.minoh.lumiris_backend.dto.in;

public record CertificationRequest(
        String name,
        String customName,
        String licenseNumber
) {}
