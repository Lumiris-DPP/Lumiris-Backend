package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.KybDetailsRequest;
import com.minoh.lumiris_backend.dto.out.KybDetailsResponse;
import com.minoh.lumiris_backend.entity.KybDetails;
import com.minoh.lumiris_backend.entity.LegalCategory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Shared request/response mapping for the KYB dossier — identical shape for artisan and repairer. */
@Component
public class KybMapper {

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
                kyb.getIdDocFileId() != null,
                kyb.getKbisFileId() != null,
                kyb.getProofOfAddressFileId() != null,
                kyb.getRibFileId() != null,
                kyb.getSireneSiren(),
                kyb.getSireneSiegeAddress(),
                kyb.getSireneNatureJuridique(),
                kyb.getSireneDirigeantsJson()
        );
    }
}
