package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.MaterialRequest;
import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppFormSummaryResponse;
import com.minoh.lumiris_backend.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DppFormMapper {

    public DppFormSummaryResponse toSummaryResponse(DppForm form) {
        return new DppFormSummaryResponse(
                form.getId(),
                form.getCreatedAt(),
                form.getStatus(),
                form.getProductName(),
                form.getProductCategory(),
                form.getSku()
        );
    }

    public DppForm toEntity(DppFormRequest request, User user) {
        DppForm form = new DppForm();
        form.setUser(user);
        form.setProductName(request.productName());
        form.setProductDescription(request.productDescription());
        form.setProductCategory(request.productCategory());
        form.setOriginCountry(request.originCountry());
        form.setManufacturedAt(request.manufacturedAt());
        form.setBatchNumber(request.batchNumber());
        form.setGtin(request.gtin());
        form.setSku(request.sku());
        form.setReachCompliant(request.reachCompliant());
        form.setRecycledPct(request.recycledPct());
        form.setWarrantyDescription(request.warrantyDescription());
        form.setIsRepairable(request.isRepairable());
        form.setEndOfLifeInstructions(request.endOfLifeInstructions());
        form.setAvailableSizes(request.availableSizes());
        form.setColors(request.colors());
        form.setCareNotes(request.careNotes());

        if (request.materials() != null) {
            request.materials().forEach(m -> {
                DppMaterial material = new DppMaterial();
                material.setDppForm(form);
                material.setFiber(m.fiber());
                material.setPercentage(m.percentage());
                material.setOriginCountry(m.originCountry());
                form.getMaterials().add(material);
            });
        }

        if (request.careInstructions() != null) {
            request.careInstructions().forEach(code -> {
                DppCareInstruction care = new DppCareInstruction();
                care.setDppForm(form);
                care.setCareCode(code);
                form.getCareInstructions().add(care);
            });
        }

        return form;
    }

    public DppFormResponse toResponse(DppForm form, String mainPhotoUrl, List<DppFormDocumentResponse> documents) {
        List<MaterialRequest> materials = form.getMaterials().stream()
                .map(m -> new MaterialRequest(m.getFiber(), m.getPercentage(), m.getOriginCountry()))
                .toList();

        List<String> careInstructions = form.getCareInstructions().stream()
                .map(DppCareInstruction::getCareCode)
                .toList();

        return new DppFormResponse(
                form.getId(),
                form.getPublicCode(),
                form.getCreatedAt(),
                form.getStatus(),
                form.getProductName(),
                form.getProductDescription(),
                form.getProductCategory(),
                form.getOriginCountry(),
                form.getAvailableSizes(),
                form.getColors(),
                mainPhotoUrl,
                materials,
                careInstructions,
                form.getCareNotes(),
                form.getManufacturedAt(),
                form.getBatchNumber(),
                form.getGtin(),
                form.getSku(),
                form.getReachCompliant(),
                form.getRecycledPct(),
                form.getWarrantyDescription(),
                form.getIsRepairable(),
                form.getEndOfLifeInstructions(),
                documents
        );
    }
}
