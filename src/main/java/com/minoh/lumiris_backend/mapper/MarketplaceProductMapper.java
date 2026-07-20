package com.minoh.lumiris_backend.mapper;

import com.minoh.lumiris_backend.dto.in.ProductForm;
import com.minoh.lumiris_backend.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceProductMapper {

    // Remplacement complet (PUT). dppForm résolu par le service (peut être null pour délier).
    public void applyUpdate(MarketplaceProduct p, UpdateProductRequest req, DppForm dppForm) {
        applyForm(p, req, dppForm);
        if (req.status() != null) {
            p.setStatus(req.status());
        }
    }

    // Vue canonique d'un produit (CRUD, recherche, suggestions). Le score comparable
    // provient exclusivement du DPP lié ; atelierPlus est résolu à la volée par le service.
    // salesCount = ventes réglées de cette annonce (0 sur les chemins publics).
    public MarketplaceItemResponse toResponse(MarketplaceProduct p, IrisScore score, boolean atelierPlus) {
        return toResponse(p, score, atelierPlus, 0L);
    }

    public MarketplaceItemResponse toResponse(MarketplaceProduct p, IrisScore score, boolean atelierPlus,
                                              long salesCount) {
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
                p.getStock(),
                p.getExternalOrderUrl(),
                p.getPhotoUrl(),
                p.getStatus(),
                score != null ? score.getTotal() : null,
                score != null ? score.getGrade() : null,
                atelierPlus,
                p.getStripePriceId() != null,
                p.getCreatedAt(),
                p.getViews(),
                salesCount
        );
    }

    // Champs communs à la création et à la mise à jour (le statut est traité par l'appelant).
    private void applyForm(MarketplaceProduct p, ProductForm req, DppForm dppForm) {
        p.setDppForm(dppForm);
        p.setName(req.name());
        p.setDescription(req.description());
        p.setCategory(req.category());
        p.setMaterial(req.material());
        p.setOriginCountry(req.originCountry());
        p.setPriceCents(req.priceCents());
        p.setCurrency(normalizeCurrency(req.currency()));
        p.setStock(req.stock());
        p.setExternalOrderUrl(req.externalOrderUrl());
        p.setPhotoUrl(req.photoUrl());
    }

    private static String normalizeCurrency(String currency) {
        return currency != null && !currency.isBlank() ? currency.toUpperCase() : "EUR";
    }
}
