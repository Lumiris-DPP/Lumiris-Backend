package com.minoh.lumiris_backend.dto.in;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Full KYB dossier submitted after the fast SIRET-only registration. Shared shape between
 * artisan and repairer onboarding — same legal-entity + legal-representative fields either way.
 */
public record KybDetailsRequest(
        // 0 = Solo (entreprise individuelle), 1 = Company, 2 = Association
        @NotNull @Min(0) @Max(2) Integer category,
        @NotBlank String businessEntity,
        String vatNumber,
        @NotBlank String addressLine1,
        @NotBlank String addressCity,
        @NotBlank String addressPostalCode,
        @NotBlank String addressCountry,

        @NotBlank String repFirstName,
        @NotBlank String repLastName,
        @NotNull LocalDate repBirthDate,
        @NotBlank String repNationality,
        @NotBlank String repAddressLine1,
        @NotBlank String repAddressCity,
        @NotBlank String repAddressPostalCode,
        @NotBlank String repAddressCountry,
        boolean repIsUbo,
        @Min(0) @Max(100) Integer repOwnershipPercentage,

        boolean termsAccepted
) {}
