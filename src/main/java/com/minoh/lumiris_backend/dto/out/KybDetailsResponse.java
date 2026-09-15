package com.minoh.lumiris_backend.dto.out;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minoh.lumiris_backend.entity.KybStatus;

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

        KybStatus kybStatus,
        String kybReviewNote,

        boolean idDocUploaded,
        String idDocUrl,
        LocalDate idDocExpiresAt,
        // null = not checked (PDF upload, or OCR unavailable); true/false = declared rep's name
        // found (or not) in the document's OCR'd text — a hint for the admin, not a certified check.
        Boolean idDocNameMatch,

        boolean kbisUploaded,
        String kbisUrl,
        LocalDate kbisExpiresAt,

        boolean proofOfAddressUploaded,
        String proofOfAddressUrl,
        LocalDate proofOfAddressExpiresAt,

        boolean ribUploaded,
        String ribUrl,
        LocalDate ribExpiresAt,

        // Read-only SIRENE snapshot, for admin comparison against the declared fields above.
        String sireneSiren,
        String sireneSiegeAddress,
        String sireneNatureJuridique,
        String sireneDirigeantsJson
) {}
