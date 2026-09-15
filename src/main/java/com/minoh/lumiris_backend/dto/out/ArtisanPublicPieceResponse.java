package com.minoh.lumiris_backend.dto.out;

public record ArtisanPublicPieceResponse(
        String publicCode,
        String productName,
        String productCategory,
        String mainPhotoUrl,
        Double irisTotal,
        String irisGrade
) {}
