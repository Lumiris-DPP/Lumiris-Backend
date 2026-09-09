package com.minoh.lumiris_backend.dto.in;

import com.minoh.lumiris_backend.entity.RepairerSource;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RepairerImportRequest(
        @NotNull RepairerSource source,
        List<String> departments,
        List<String> nafCodes,
        Integer maxPages
) {}
