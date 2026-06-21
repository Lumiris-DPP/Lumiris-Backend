package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import org.springframework.stereotype.Component;

@Component
public class DppFormMapper {

    public DppForm toEntity(DppFormRequest request) {
        DppForm form = new DppForm();
        applyPatch(request, form);
        return form;
    }

    public void applyPatch(DppFormRequest patch, DppForm form) {
        if (patch.productName() != null)       form.setProductName(patch.productName());
        if (patch.productType() != null)       form.setProductType(patch.productType());
        if (patch.internalReference() != null) form.setInternalReference(patch.internalReference());
        if (patch.retailPrice() != null)       form.setRetailPrice(patch.retailPrice());
        if (patch.currency() != null)          form.setCurrency(patch.currency());
        if (patch.status() != null)            form.setStatus(patch.status());
    }

    public DppFormResponse toResponse(DppForm form) {
        return new DppFormResponse(
                form.getId(),
                form.getProductName(),
                form.getProductType(),
                form.getInternalReference(),
                form.getRetailPrice(),
                form.getCurrency(),
                form.getStatus(),
                form.getCreatedAt(),
                form.getUpdatedAt()
        );
    }
}
