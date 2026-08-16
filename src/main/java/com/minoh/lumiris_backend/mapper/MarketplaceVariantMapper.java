package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.out.ProductVariantResponse;
import com.minoh.lumiris_backend.dto.out.SizeMeasurementResponse;
import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceVariantMapper {

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
