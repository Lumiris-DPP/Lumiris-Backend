package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.ProductVariantForm;
import com.minoh.lumiris_backend.dto.in.SizeMeasurementForm;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.MarketplaceVariantMapper;
import com.minoh.lumiris_backend.repository.MarketplaceProductVariantRepository;
import com.minoh.lumiris_backend.repository.MarketplaceSizeMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Déclinaisons d'une annonce et son guide des mesures. Ces écritures n'ont de sens qu'avec celle de
// l'annonce : MANDATORY exige la transaction du catalogue de l'atelier, si bien qu'une déclinaison
// ou une mesure invalide annule toute l'écriture, produit compris, au lieu d'en laisser une moitié.
@Service
@RequiredArgsConstructor
public class MarketplaceVariantService {

    private final MarketplaceProductVariantRepository variantRepository;
    private final MarketplaceSizeMeasurementRepository measurementRepository;
    private final MarketplaceVariantMapper variantMapper;

    // Remplace les déclinaisons puis le guide des mesures, dont les tailles doivent exister parmi les
    // déclinaisons retenues.
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceVariantsAndSizeGuide(MarketplaceProduct product, List<ProductVariantForm> variants,
                                            List<SizeMeasurementForm> sizeGuide) {
        Set<String> sizes = applyVariants(product, variants);
        applySizeGuide(product, sizeGuide, sizes);
    }

    // Une annonce convertie sans grille de déclinaisons garde le comportement d'origine : une seule
    // déclinaison sans libellé, qui porte tout le stock et n'affiche aucun sélecteur à l'acheteur.
    @Transactional(propagation = Propagation.MANDATORY)
    public void seedDefaultVariant(MarketplaceProduct product, int stock) {
        List<MarketplaceProductVariant> existing =
                variantRepository.findByProduct_IdOrderByPositionAscIdAsc(product.getId());
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

    // Réconciliation du remplacement complet : les lignes identifiées sont mises à jour, les
    // nouvelles insérées, les absentes supprimées. Renvoie les tailles retenues, seules autorisées
    // dans le guide des mesures.
    private Set<String> applyVariants(MarketplaceProduct product, List<ProductVariantForm> forms) {
        List<ProductVariantForm> normalized = normalizeVariants(forms);
        if (normalized.isEmpty()) {
            throw new BillingValidationException("Une annonce doit avoir au moins une déclinaison.");
        }
        assertDistinctCombinations(normalized);

        Map<UUID, MarketplaceProductVariant> existing = variantRepository
                .findByProduct_IdOrderByPositionAscIdAsc(product.getId()).stream()
                .collect(Collectors.toMap(MarketplaceProductVariant::getId, v -> v));

        // Les déclinaisons retirées partent AVANT que les nouvelles n'arrivent : Hibernate ordonne
        // ses insertions avant ses suppressions au flush, et une combinaison libérée puis reprise
        // violerait sinon l'index unique.
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

    // Remplace le guide des mesures : une cote par taille et par libellé, sur une taille existante.
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

    // Écarte les lignes vides, rogne les libellés et ramène un stock négatif à zéro.
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

    // Pré-contrôle métier : sans lui l'index unique remonterait en 500 au flush.
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

    // La version n'est incrémentée QUE par les requêtes de stock atomiques (vente, remboursement) :
    // elle signifie exactement « le stock a bougé sous toi ». L'incrémenter aussi à l'enregistrement
    // artisan ferait échouer deux bascules de visibilité successives, sans qu'aucune vente n'ait eu lieu.
    private static void assertFreshVersion(ProductVariantForm form, MarketplaceProductVariant variant) {
        if (form.version() != null && form.version() != variant.getVersion()) {
            throw new ConflictException(
                    "Le stock de cette annonce a changé pendant votre saisie. "
                            + "Rechargez la page avant d'enregistrer.");
        }
    }

    // Libellé rogné, ou null s'il ne reste rien.
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
