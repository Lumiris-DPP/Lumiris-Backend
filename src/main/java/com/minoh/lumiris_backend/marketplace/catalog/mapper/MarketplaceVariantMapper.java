package com.minoh.lumiris_backend.marketplace.catalog.mapper;

import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.ProductVariantResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.SizeMeasurementResponse;
import org.springframework.stereotype.Component;

/** Assemble les réponses et les libellés des déclinaisons et mesures. */
@Component
public class MarketplaceVariantMapper {

    /** Construit la réponse publique à partir des données chargées. */
    public ProductVariantResponse toResponse(MarketplaceProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSizeLabel(),
                variant.getColorLabel(),
                variant.getColorHex(),
                variant.getSku(),
                variant.getStock(),
                variant.getPosition(),
                variant.getVersion());
    }

    /** Construit la réponse publique à partir des données chargées. */
    public SizeMeasurementResponse toResponse(MarketplaceSizeMeasurement measurement) {
        return new SizeMeasurementResponse(
                measurement.getSizeLabel(),
                measurement.getLabel(),
                measurement.getValueMm(),
                measurement.getPosition());
    }

    // Libellé lisible d'une déclinaison, nul quand aucun axe n'est renseigné : une annonce sans
    // taille ni couleur doit s'afficher exactement comme avant la feature.
    public String label(MarketplaceProductVariant variant) {
        return label(variant.getSizeLabel(), variant.getColorLabel());
    }

    /** Compose le libellé de la déclinaison avec ses attributs disponibles. */
    public String label(String sizeLabel, String colorLabel) {
        boolean hasSize = sizeLabel != null && !sizeLabel.isBlank();
        boolean hasColor = colorLabel != null && !colorLabel.isBlank();
        if (hasSize && hasColor) {
            return sizeLabel + " · " + colorLabel;
        }
        if (hasSize) {
            return sizeLabel;
        }
        return hasColor ? colorLabel : null;
    }
}
