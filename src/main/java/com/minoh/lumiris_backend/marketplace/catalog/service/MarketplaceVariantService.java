package com.minoh.lumiris_backend.marketplace.catalog.service;

import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.ProductVariantForm;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.SizeMeasurementForm;
import com.minoh.lumiris_backend.marketplace.catalog.mapper.MarketplaceVariantMapper;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductVariantRepository;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceSizeMeasurementRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketplaceVariantService {

    private final MarketplaceProductVariantRepository variantRepository;
    private final MarketplaceSizeMeasurementRepository measurementRepository;
    private final MarketplaceVariantMapper variantMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceVariantsAndSizeGuide(MarketplaceProduct product, List<ProductVariantForm> variants,
                                            List<SizeMeasurementForm> sizeGuide) {
        Set<String> sizes = applyVariants(product, variants);
        applySizeGuide(product, sizeGuide, sizes);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void seedDefaultVariant(MarketplaceProduct product, int stock) {
        List<MarketplaceProductVariant> existing = variantRepository.lockByProductId(product.getId());
        if (existing.isEmpty()) {
            MarketplaceProductVariant variant = new MarketplaceProductVariant();
            variant.setProduct(product);
            variant.setStock(Math.max(0, stock));
            variantRepository.save(variant);
            return;
        }
        MarketplaceProductVariant only = existing.get(0);
        if (existing.size() == 1 && variantMapper.label(only) == null) {
            only.setStock(Math.max(0, stock));
            variantRepository.save(only);
        }
    }

    private Set<String> applyVariants(MarketplaceProduct product, List<ProductVariantForm> forms) {
        List<ProductVariantForm> normalized = normalizeVariants(forms);
        if (normalized.isEmpty()) {
            throw new BillingValidationException("Une annonce doit avoir au moins une déclinaison.");
        }
        assertDistinctCombinations(normalized);

        Map<UUID, MarketplaceProductVariant> existing = variantRepository
                .lockByProductId(product.getId()).stream()
                .collect(Collectors.toMap(MarketplaceProductVariant::getId, v -> v));

        Set<UUID> kept = normalized.stream()
                .map(ProductVariantForm::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<UUID> removed = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!removed.isEmpty()) {
            variantRepository.deleteAllByIdInBatch(removed);
        }

        Set<String> sizes = new LinkedHashSet<>();
        int position = 0;

        for (ProductVariantForm form : normalized) {
            MarketplaceProductVariant variant;
            if (form.id() != null) {
                variant = existing.get(form.id());
                if (variant == null) {
                    throw new ResourceNotFoundException("Déclinaison introuvable");
                }
                assertFreshVersion(form, variant);
            } else {
                variant = new MarketplaceProductVariant();
                variant.setProduct(product);
            }
            variant.setSizeLabel(form.sizeLabel());
            variant.setColorLabel(form.colorLabel());
            variant.setColorHex(form.colorHex());
            variant.setSku(form.sku());
            variant.setStock(form.stock());
            variant.setPosition(position++);
            variantRepository.save(variant);
            if (form.sizeLabel() != null) {
                sizes.add(form.sizeLabel());
            }
        }
        return sizes;
    }

    private void applySizeGuide(MarketplaceProduct product, List<SizeMeasurementForm> forms, Set<String> sizes) {
        measurementRepository.deleteByProductId(product.getId());
        if (forms == null || forms.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        int position = 0;
        for (SizeMeasurementForm form : forms) {
            String sizeLabel = trimToNull(form.sizeLabel());
            String label = trimToNull(form.label());
            if (sizeLabel == null || label == null) {
                continue;
            }
            if (!sizes.contains(sizeLabel)) {
                throw new BillingValidationException(
                        "Le guide des tailles mentionne une taille absente du produit : « " + sizeLabel + " ».");
            }
            if (!seen.add(sizeLabel + "\u0000" + label.toLowerCase(Locale.ROOT))) {
                throw new BillingValidationException(
                        "La mesure « " + label + " » est renseignée deux fois pour la taille « " + sizeLabel + " ».");
            }
            MarketplaceSizeMeasurement measurement = new MarketplaceSizeMeasurement();
            measurement.setProduct(product);
            measurement.setSizeLabel(sizeLabel);
            measurement.setLabel(label);
            measurement.setValueMm(form.valueMm());
            measurement.setPosition(form.position() > 0 ? form.position() : position);
            measurementRepository.save(measurement);
            position++;
        }
    }

    private static List<ProductVariantForm> normalizeVariants(List<ProductVariantForm> forms) {
        if (forms == null) {
            return List.of();
        }
        return forms.stream()
                .filter(Objects::nonNull)
                .map(f -> new ProductVariantForm(f.id(), trimToNull(f.sizeLabel()), trimToNull(f.colorLabel()),
                        trimToNull(f.colorHex()), trimToNull(f.sku()), Math.max(0, f.stock()), f.position(),
                        f.version()))
                .toList();
    }

    private static void assertDistinctCombinations(List<ProductVariantForm> forms) {
        Set<String> seen = new HashSet<>();
        for (ProductVariantForm form : forms) {
            String key = Objects.toString(form.sizeLabel(), "").toLowerCase(Locale.ROOT)
                    + "\u0000" + Objects.toString(form.colorLabel(), "").toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new BillingValidationException(
                        "Deux déclinaisons portent la même combinaison de taille et de couleur.");
            }
        }
    }

    private static void assertFreshVersion(ProductVariantForm form, MarketplaceProductVariant variant) {
        if (form.version() != null && form.version() != variant.getVersion()) {
            throw new ConflictException(
                    "Le stock de cette annonce a changé pendant votre saisie. "
                            + "Rechargez la page avant d'enregistrer.");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
