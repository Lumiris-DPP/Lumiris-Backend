package com.minoh.lumiris_backend.marketplace.catalog.dto.in;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// Porte les caractéristiques et le stock d'une déclinaison.
public record ProductVariantForm(
        UUID id,
        @Size(max = 40) String sizeLabel,
        @Size(max = 40) String colorLabel,
        @Size(max = 7) @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Couleur invalide") String colorHex,
        @Size(max = 64) String sku,
        @Min(0) int stock,
        @Min(0) int position,
        Long version
) {}
