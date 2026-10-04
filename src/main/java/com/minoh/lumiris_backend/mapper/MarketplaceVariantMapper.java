package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import com.minoh.lumiris_backend.dto.out.ProductVariantResponse;
import com.minoh.lumiris_backend.dto.out.SizeMeasurementResponse;
import org.springframework.stereotype.Component;

// Prépare les déclinaisons et les mesures du catalogue.
@Component
public class MarketplaceVariantMapper {

    // Présente les caractéristiques et le stock de la déclinaison.
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

    // Présente les caractéristiques de la mesure du guide des tailles.
    public SizeMeasurementResponse toResponse(MarketplaceSizeMeasurement measurement) {
        return new SizeMeasurementResponse(
                measurement.getSizeLabel(),
                measurement.getLabel(),
                measurement.getValueMm(),
                measurement.getPosition());
    }

    // Compose le libellé de taille et de couleur.
    public String label(MarketplaceProductVariant variant) {
        return label(variant.getSizeLabel(), variant.getColorLabel());
    }

    // Compose le libellé de taille et de couleur.
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
