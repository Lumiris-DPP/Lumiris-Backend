package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.dto.out.ProductVariantResponse;
import com.minoh.lumiris_backend.dto.out.SizeMeasurementResponse;
import com.minoh.lumiris_backend.mapper.MarketplaceProductMapper;
import com.minoh.lumiris_backend.mapper.MarketplaceVariantMapper;
import com.minoh.lumiris_backend.mapper.ProductPresentation;
import com.minoh.lumiris_backend.repository.MarketplaceProductVariantRepository;
import com.minoh.lumiris_backend.repository.MarketplaceSizeMeasurementRepository;
import com.minoh.lumiris_backend.service.AtelierPlusResolver;
import com.minoh.lumiris_backend.service.PreparationDelayResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Réunit les informations nécessaires à la présentation des pièces.
@Service
@RequiredArgsConstructor
public class MarketplaceItemAssembler {

    private final AtelierPlusResolver atelierPlusResolver;
    private final PreparationDelayResolver preparationDelayResolver;
    private final MarketplaceProductVariantRepository variantRepository;
    private final MarketplaceSizeMeasurementRepository measurementRepository;
    private final MarketplaceProductMapper mapper;
    private final MarketplaceVariantMapper variantMapper;

    // Charge les pièces et leurs scores pour la présentation.
    public List<ScoredProduct> fetch(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new ScoredProduct((MarketplaceProduct) r[0], (IrisScore) r[1]))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // Présente les pièces avec les informations regroupées de leurs ateliers.
    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> toResponses(List<ScoredProduct> rows) {
        return toResponses(rows, Map.of());
    }

    // Présente les pièces avec les informations regroupées de leurs ateliers.
    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> toResponses(List<ScoredProduct> rows, Map<UUID, Long> salesByProduct) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> productIds = rows.stream().map(sp -> sp.product().getId()).collect(Collectors.toSet());
        Map<UUID, List<ProductVariantResponse>> variants = variantsByProduct(productIds);
        Map<UUID, List<SizeMeasurementResponse>> guides = guidesByProduct(productIds);
        Set<UUID> plusIds = atelierPlusResolver.atelierPlusUserIds(userIdsOf(rows));
        Instant now = Instant.now();

        return rows.stream()
                .map(sp -> mapper.toResponse(sp.product(), sp.score(), new ProductPresentation(
                        variants.getOrDefault(sp.product().getId(), List.of()),
                        guides.getOrDefault(sp.product().getId(), List.of()),
                        plusIds.contains(sp.artisanUserId()),
                        preparationDelayResolver.effectiveDays(sp.product(), now),
                        preparationDelayResolver.activePauseUntil(sp.product().getArtisanProfile(), now),
                        salesByProduct.getOrDefault(sp.product().getId(), 0L))))
                .toList();
    }

    // Présente une pièce avec ses informations complémentaires.
    @Transactional(readOnly = true)
    public MarketplaceItemResponse toResponse(ScoredProduct row) {
        return toResponses(List.of(row)).get(0);
    }

    // Identifie les comptes ateliers associés aux pièces présentées.
    public static Set<UUID> userIdsOf(List<ScoredProduct> rows) {
        return rows.stream().map(ScoredProduct::artisanUserId).collect(Collectors.toSet());
    }

    // Regroupe les déclinaisons chargées par pièce.
    private Map<UUID, List<ProductVariantResponse>> variantsByProduct(Collection<UUID> productIds) {
        return variantRepository.findByProduct_IdInOrderByPositionAscIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId(),
                        Collectors.mapping(variantMapper::toResponse, Collectors.toList())));
    }

    // Regroupe les mesures chargées par pièce.
    private Map<UUID, List<SizeMeasurementResponse>> guidesByProduct(Collection<UUID> productIds) {
        return measurementRepository.findByProduct_IdInOrderByPositionAscLabelAsc(productIds).stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId(),
                        Collectors.mapping(variantMapper::toResponse, Collectors.toList())));
    }
}
