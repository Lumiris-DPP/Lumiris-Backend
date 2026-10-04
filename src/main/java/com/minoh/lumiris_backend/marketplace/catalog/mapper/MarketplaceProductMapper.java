package com.minoh.lumiris_backend.marketplace.catalog.mapper;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.ProductVariantResponse;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceProductMapper {

    public void applyUpdate(MarketplaceProduct p, UpdateProductRequest req, DppForm dppForm) {
        p.setDppForm(dppForm);
        p.setName(req.name());
        p.setDescription(req.description());
        p.setCategory(req.category());
        p.setMaterial(req.material());
        p.setOriginCountry(req.originCountry());
        p.setPriceCents(req.priceCents());
        p.setCurrency(normalizeCurrency(req.currency()));
        p.setShippingCents(req.shippingCents() != null ? Math.max(0, req.shippingCents()) : 0);
        p.setReturnPolicy(req.returnPolicy());
        p.setPreparationDays(req.preparationDays() != null ? req.preparationDays() : 0);
        p.setWeightGrams(req.weightGrams() != null ? Math.max(0, req.weightGrams()) : 0);
        p.setExternalOrderUrl(req.externalOrderUrl());
        p.setPhotoUrl(req.photoUrl());
        if (req.status() != null) {
            p.setStatus(req.status());
        }
    }

    public MarketplaceItemResponse toResponse(MarketplaceProduct p, IrisScore score,
                                              ProductPresentation presentation) {
        ArtisanProfile artisan = p.getArtisanProfile();
        String artisanName = artisan.getAtelierName() != null ? artisan.getAtelierName() : artisan.getDisplayName();
        return new MarketplaceItemResponse(
                p.getId(),
                artisan.getId(),
                artisanName,
                p.getDppForm() != null ? p.getDppForm().getId() : null,
                p.getName(),
                p.getDescription(),
                p.getCategory(),
                p.getMaterial(),
                p.getOriginCountry(),
                p.getPriceCents(),
                p.getCurrency(),
                totalStock(presentation),
                presentation.variants(),
                presentation.sizeGuide(),
                p.getShippingCents(),
                p.getReturnPolicy(),
                p.getDppForm() != null ? p.getDppForm().getWarrantyDescription() : null,
                p.getPreparationDays(),
                presentation.effectivePreparationDays(),
                presentation.atelierPausedUntil(),
                p.getWeightGrams(),
                p.getExternalOrderUrl(),
                p.getPhotoUrl(),
                p.getStatus(),
                score != null ? score.getTotal() : null,
                score != null ? score.getGrade() : null,
                presentation.atelierPlus(),
                p.getStripePriceId() != null,
                p.getCreatedAt(),
                p.getViews(),
                presentation.salesCount()
        );
    }

    private static int totalStock(ProductPresentation presentation) {
        return presentation.variants().stream().mapToInt(ProductVariantResponse::stock).sum();
    }

    private static String normalizeCurrency(String currency) {
        return currency != null && !currency.isBlank() ? currency.toUpperCase() : "EUR";
    }
}
