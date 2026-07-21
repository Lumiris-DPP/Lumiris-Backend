package com.minoh.lumiris_backend.dto.out;

public record MaterialResponse(
        String fiber,
        Integer percentage,
        String originCountry,
        Double latitude,
        Double longitude
) {}