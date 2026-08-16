package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Une cote relevée par l'atelier, en millimètres entiers.
public record SizeMeasurementForm(
        @NotBlank @Size(max = 40) String sizeLabel,
        @NotBlank @Size(max = 60) String label,
        @Min(1) @Max(100_000) int valueMm,
        @Min(0) int position
) {}
