package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.MaterialRequest;
import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppFormSummaryResponse;
import com.minoh.lumiris_backend.dto.out.MaterialResponse;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DppFormMapper {

    private final GeocodingService geocodingService;

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
        applyScalars(form, request);
        addChildren(form, request);
        return form;
    }

    public void applyScalars(DppForm form, DppFormRequest request) {
        form.setProductName(request.productName());
        form.setProductDescription(request.productDescription());
        form.setProductCategory(request.productCategory());
        form.setOriginCountry(request.originCountry());
        form.setManufacturedAt(request.manufacturedAt());
        form.setBatchNumber(request.batchNumber());
        form.setQuantity(request.quantity() != null && request.quantity() >= 1 ? request.quantity() : 1);
        form.setGtin(request.gtin());
        form.setSku(request.sku());
        // NOT NULL in DB (default false); a draft may omit them, so coalesce.
        form.setReachCompliant(request.reachCompliant() != null && request.reachCompliant());
        form.setRecycledPct(request.recycledPct());
        form.setWarrantyDescription(request.warrantyDescription());
        form.setWarrantyMonths(request.warrantyMonths());
        form.setIsRepairable(request.isRepairable() != null && request.isRepairable());
        form.setEndOfLifeInstructions(request.endOfLifeInstructions());
        form.setAvailableSizes(request.availableSizes());
        form.setColors(request.colors());
        form.setCareNotes(request.careNotes());
    }

    /**
     * Fabrique unique d'une matière : le géocodage du pays d'origine doit valoir pour toute
     * écriture (création comme mise à jour d'un brouillon), sans quoi un brouillon réenregistré
     * perdrait ses coordonnées et la carte des origines serait vide à la publication.
     */
    public DppMaterial toMaterial(DppForm form, MaterialRequest request) {
        DppMaterial material = new DppMaterial();
        material.setDppForm(form);
        material.setFiber(request.fiber());
        material.setPercentage(request.percentage());
        material.setOriginCountry(request.originCountry());
        geocodingService.geocode(request.originCountry()).ifPresent(coordinates -> {
            material.setLatitude(coordinates.latitude());
            material.setLongitude(coordinates.longitude());
        });
        return material;
    }

    public void addChildren(DppForm form, DppFormRequest request) {
        if (request.materials() != null) {
            request.materials().forEach(m -> form.getMaterials().add(toMaterial(form, m)));
        }

        if (request.careInstructions() != null) {
            request.careInstructions().forEach(code -> {
                DppCareInstruction care = new DppCareInstruction();
                care.setDppForm(form);
                care.setCareCode(code);
                form.getCareInstructions().add(care);
            });
        }
    }

    public DppFormResponse toResponse(
            DppForm form,
            String mainPhotoUrl,
            List<DppFormDocumentResponse> documents,
            String artisanSlug
    ) {
        List<MaterialResponse> materials = form.getMaterials().stream()
                .map(m -> new MaterialResponse(m.getFiber(), m.getPercentage(), m.getOriginCountry(), m.getLatitude(), m.getLongitude()))
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
                form.getWarrantyMonths(),
                form.getIsRepairable(),
                form.getEndOfLifeInstructions(),
                form.getDataHash(),
                form.getBlockchainAnchorStatus(),
                form.getBlockchainTxHash(),
                documents,
                artisanSlug
        );
    }

    public Map<String, Object> toHashableData(DppForm dppForm) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productName", dppForm.getProductName());
        data.put("productDescription", dppForm.getProductDescription());
        data.put("productCategory", dppForm.getProductCategory());
        data.put("originCountry", dppForm.getOriginCountry());
        data.put("availableSizes", dppForm.getAvailableSizes());
        data.put("colors", dppForm.getColors());
        data.put("manufacturedAt", dppForm.getManufacturedAt());
        data.put("batchNumber", dppForm.getBatchNumber());
        data.put("gtin", dppForm.getGtin());
        data.put("sku", dppForm.getSku());
        data.put("reachCompliant", dppForm.getReachCompliant());
        data.put("recycledPct", dppForm.getRecycledPct());
        data.put("warrantyDescription", dppForm.getWarrantyDescription());
        // N'AJOUTER AUCUNE CLÉ ICI : verify() recompare ce condensé à celui déjà ancré on-chain,
        // donc tout champ ajouté ferait échouer la vérification de TOUS les passeports existants.
        data.put("isRepairable", dppForm.getIsRepairable());
        data.put("endOfLifeInstructions", dppForm.getEndOfLifeInstructions());
        data.put("materials", dppForm.getMaterials().stream()
                .map(m -> Map.of("fiber", m.getFiber(), "percentage", m.getPercentage(), "originCountry", String.valueOf(m.getOriginCountry())))
                .toList());
        data.put("careInstructions", dppForm.getCareInstructions().stream()
                .map(c -> Map.of("careCode", c.getCareCode()))
                .toList());
        return data;
    }
}
