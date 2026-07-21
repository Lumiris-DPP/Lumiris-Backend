package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.KybDetailsRequest;
import com.minoh.lumiris_backend.dto.out.KybDetailsResponse;
import com.minoh.lumiris_backend.entity.KybDetails;
import com.minoh.lumiris_backend.entity.KybStatus;
import com.minoh.lumiris_backend.entity.LegalCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Shared request/response mapping for the KYB dossier — identical shape for artisan and repairer. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KybMapper {

    private static final Pattern NON_LETTERS = Pattern.compile("[^A-Z]");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

    private final StorageService storageService;

    public void applyRequest(KybDetails kyb, KybDetailsRequest request) {
        kyb.setLegalCategory(LegalCategory.fromCode(request.category()));
        kyb.setBusinessEntity(request.businessEntity());
        kyb.setVatNumber(request.vatNumber());
        kyb.setAddressLine1(request.addressLine1());
        kyb.setAddressCity(request.addressCity());
        kyb.setAddressPostalCode(request.addressPostalCode());
        kyb.setAddressCountry(request.addressCountry());

        kyb.setRepFirstName(request.repFirstName());
        kyb.setRepLastName(request.repLastName());
        kyb.setRepBirthDate(request.repBirthDate());
        kyb.setRepNationality(request.repNationality());
        kyb.setRepAddressLine1(request.repAddressLine1());
        kyb.setRepAddressCity(request.repAddressCity());
        kyb.setRepAddressPostalCode(request.repAddressPostalCode());
        kyb.setRepAddressCountry(request.repAddressCountry());
        kyb.setRepIsUbo(request.repIsUbo());
        kyb.setRepOwnershipPercentage(request.repOwnershipPercentage());

        if (request.termsAccepted()) {
            // Captured at the real consent moment (this call), never backdated or defaulted.
            kyb.setTermsAcceptedAt(Instant.now());
            // A (re)submission — including after an INCOMPLETE/REJECTED admin verdict — goes
            // back into the review queue.
            kyb.setKybStatus(KybStatus.PENDING);
        }
    }

    public KybDetailsResponse toResponse(KybDetails kyb) {
        return new KybDetailsResponse(
                kyb.getLegalCategory() != null ? kyb.getLegalCategory().code() : null,
                kyb.getBusinessEntity(),
                kyb.getVatNumber(),
                kyb.getAddressLine1(),
                kyb.getAddressCity(),
                kyb.getAddressPostalCode(),
                kyb.getAddressCountry(),
                kyb.getTermsAcceptedAt(),
                kyb.getRepFirstName(),
                kyb.getRepLastName(),
                kyb.getRepBirthDate(),
                kyb.getRepNationality(),
                kyb.getRepAddressLine1(),
                kyb.getRepAddressCity(),
                kyb.getRepAddressPostalCode(),
                kyb.getRepAddressCountry(),
                Boolean.TRUE.equals(kyb.getRepIsUbo()),
                kyb.getRepOwnershipPercentage(),
                kyb.getKybStatus(),
                kyb.getKybReviewNote(),
                kyb.getIdDocFileId() != null,
                presignedUrl(kyb.getIdDocFileId()),
                kyb.getIdDocExpiresAt(),
                computeNameMatch(kyb),
                kyb.getKbisFileId() != null,
                presignedUrl(kyb.getKbisFileId()),
                kyb.getKbisExpiresAt(),
                kyb.getProofOfAddressFileId() != null,
                presignedUrl(kyb.getProofOfAddressFileId()),
                kyb.getProofOfAddressExpiresAt(),
                kyb.getRibFileId() != null,
                presignedUrl(kyb.getRibFileId()),
                kyb.getRibExpiresAt(),
                kyb.getSireneSiren(),
                kyb.getSireneSiegeAddress(),
                kyb.getSireneNatureJuridique(),
                kyb.getSireneDirigeantsJson()
        );
    }

    private String presignedUrl(UUID fileId) {
        if (fileId == null) return null;
        try {
            return storageService.getPresignedUrl(fileId);
        } catch (RuntimeException e) {
            log.warn("Failed to build presigned URL for KYB document {}: {}", fileId, e.getMessage());
            return null;
        }
    }

    private Boolean computeNameMatch(KybDetails kyb) {
        String ocrText = kyb.getIdDocOcrText();
        String first = kyb.getRepFirstName();
        String last = kyb.getRepLastName();
        if (ocrText == null || ocrText.isBlank() || first == null || last == null) return null;
        String normalizedOcr = normalize(ocrText);
        return normalizedOcr.contains(normalize(first)) && normalizedOcr.contains(normalize(last));
    }

    private String normalize(String s) {
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return NON_LETTERS.matcher(withoutDiacritics.toUpperCase()).replaceAll("");
    }
}
