package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.ArtisanSource;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ArtisanImportRequest(
        @NotNull ArtisanSource source,
        List<String> departments,
        List<String> nafCodes,
        Integer maxPages
) {}
