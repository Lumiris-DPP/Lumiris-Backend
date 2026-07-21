package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KybDetailsResponse(
        Integer category,
        String businessEntity,
        String vatNumber,
        String addressLine1,
        String addressCity,
        String addressPostalCode,
        String addressCountry,
        Instant termsAcceptedAt,

        String repFirstName,
        String repLastName,
        LocalDate repBirthDate,
        String repNationality,
        String repAddressLine1,
        String repAddressCity,
        String repAddressPostalCode,
        String repAddressCountry,
        boolean repIsUbo,
        Integer repOwnershipPercentage,

        boolean idDocUploaded,
        boolean kbisUploaded,
        boolean proofOfAddressUploaded,
        boolean ribUploaded,

        // Read-only SIRENE snapshot, for admin comparison against the declared fields above.
        String sireneSiren,
        String sireneSiegeAddress,
        String sireneNatureJuridique,
        String sireneDirigeantsJson
) {}
