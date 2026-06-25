package com.minoh.lumiris_backend.dto.in;

public record MaterialRequest(
        String fiber,
        Integer percentage,
        String originCountry
) {}
