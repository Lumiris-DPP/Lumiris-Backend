package com.minoh.lumiris_backend.marketplace.catalog.service;

import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.SuggestRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.SearchResponse;
import com.minoh.lumiris_backend.marketplace.catalog.dto.out.SuggestionResponse;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.marketplace.decision.dto.out.DecisionLogResponse;
import com.minoh.lumiris_backend.marketplace.decision.service.MarketplaceDecisionLogService;
import com.minoh.lumiris_backend.marketplace.seller.service.AtelierPlusResolver;
import com.minoh.lumiris_backend.marketplace.seller.service.PayableSellerResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicCatalogService {

    private static final int SUGGESTION_COUNT = 3;

    private static final int MAX_QUERY_LENGTH = 200;

    private final MarketplaceProductRepository productRepository;
    private final MarketplaceItemAssembler assembler;
    private final PayableSellerResolver payableSellerResolver;
    private final AtelierPlusResolver atelierPlusResolver;
    private final MarketplaceDecisionLogService decisionLogService;

    @Transactional
    public void trackView(UUID productId) {
        productRepository.incrementViews(productId);
    }

    @Transactional(readOnly = true)
    public MarketplaceItemResponse getPublished(UUID id) {
        return firstPayable(productRepository.findScoredPublishedById(id));
    }

    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> getPublishedByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return assembler.toResponses(retainPayable(assembler.fetch(
                productRepository.findScoredPublishedByIds(ids))));
    }

    @Transactional(readOnly = true)
    public MarketplaceItemResponse getPublishedByDpp(UUID dppFormId) {
        return firstPayable(productRepository.findScoredPublishedByDpp(dppFormId));
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String query, String category, String material, String origin,
                                 String sort, List<String> personalizeCategories) {
        String q = truncate(blankToNull(query), MAX_QUERY_LENGTH);
        Map<UUID, Double> rankById = q != null
                ? textRanks(q, category, material, origin)
                : Map.of();

        String baseKey = baseSortKey(sort, q != null);
        Set<String> perso = normalizeCategories(personalizeCategories);
        String sortKey = baseKey
                + (q != null && !"TEXT_RANK_DESC".equals(baseKey) ? "/TEXT_FILTERED" : "")
                + (perso.isEmpty() ? "" : "+PERSONALIZED");

        List<ScoredProduct> rows = q != null ? textRows(rankById) : catalogueRows(category, material, origin);
        rows.sort(comparatorForKey(baseKey, rankById));
        if (!perso.isEmpty()) {

            rows.sort(Comparator.comparingInt(sp -> perso.contains(lower(sp.product().getCategory())) ? 0 : 1));
        }

        List<MarketplaceItemResponse> items = assembler.toResponses(rows);
        List<DecisionLogResponse.Entry> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ScoredProduct sp = rows.get(i);
            boolean boosted = !perso.isEmpty() && perso.contains(lower(sp.product().getCategory()));
            ranked.add(entry(i + 1, sp, items.get(i).atelierPlus(),
                    searchReason(q, baseKey, rankById.get(sp.product().getId()), boosted)));
        }

        Map<String, Object> requestEcho = new LinkedHashMap<>();
        requestEcho.put("q", nullToEmpty(q));
        requestEcho.put("category", nullToEmpty(category));
        requestEcho.put("material", nullToEmpty(material));
        requestEcho.put("origin", nullToEmpty(origin));
        requestEcho.put("sort", nullToEmpty(sort));
        requestEcho.put("personalize", perso);
        DecisionLogResponse log = decisionLogService.record("SEARCH", sortKey, requestEcho, ranked);
        return new SearchResponse(items, log);
    }

    @Transactional(readOnly = true)
    public SuggestionResponse suggest(SuggestRequest req) {
        double minTotal = req.score();
        String category = blankToNull(req.category());

        List<ScoredProduct> rows = retainPayable(assembler.fetch(
                productRepository.suggestCandidates(minTotal, category)));
        boolean relaxed = false;
        if (rows.size() < SUGGESTION_COUNT && category != null) {

            rows = retainPayable(assembler.fetch(productRepository.suggestCandidates(minTotal, null)));
            relaxed = true;
        }

        Set<UUID> plusIds = atelierPlusResolver.atelierPlusUserIds(MarketplaceItemAssembler.userIdsOf(rows));

        rows.sort(Comparator
                .comparingDouble(ScoredProduct::total).reversed()
                .thenComparing(sp -> plusIds.contains(sp.artisanUserId()) ? 0 : 1)
                .thenComparing(sp -> sp.product().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())));

        List<ScoredProduct> top = rows.stream().limit(SUGGESTION_COUNT).toList();
        String sortKey = relaxed ? "IRIS_DESC_THEN_ATELIER_PLUS/RELAXED_CATEGORY" : "IRIS_DESC_THEN_ATELIER_PLUS";

        List<MarketplaceItemResponse> items = assembler.toResponses(top);
        List<SuggestionResponse.Suggestion> suggestions = new ArrayList<>();
        List<DecisionLogResponse.Entry> ranked = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            ScoredProduct sp = top.get(i);
            boolean plus = plusIds.contains(sp.artisanUserId());
            String reason = String.format(Locale.ROOT, "score %.1f ≥ scan %.1f%s%s",
                    sp.total(), minTotal, plus ? " · ATELIER+" : "", relaxed ? " · catégorie élargie" : "");
            suggestions.add(new SuggestionResponse.Suggestion(items.get(i), i + 1, reason));
            ranked.add(entry(i + 1, sp, plus, reason));
        }
        DecisionLogResponse log = decisionLogService.record(
                "SUGGEST", sortKey,
                Map.of("category", nullToEmpty(req.category()), "grade", nullToEmpty(req.grade()),
                        "score", minTotal, "material", nullToEmpty(req.material())),
                ranked);
        return new SuggestionResponse(suggestions, log);
    }

    private List<ScoredProduct> catalogueRows(String category, String material, String origin) {
        return retainPayable(assembler.fetch(productRepository.searchPublished(
                blankToNull(category), blankToNull(material), blankToNull(origin))));
    }

    private Map<UUID, Double> textRanks(String q, String category, String material, String origin) {
        Map<UUID, Double> ranks = new LinkedHashMap<>();
        for (Object[] row : productRepository.searchPublishedTextRanked(
                q, blankToNull(category), blankToNull(material), blankToNull(origin))) {
            ranks.put(toUuid(row[0]), ((Number) row[1]).doubleValue());
        }
        return ranks;
    }

    private List<ScoredProduct> textRows(Map<UUID, Double> rankById) {
        if (rankById.isEmpty()) {
            return new ArrayList<>();
        }
        return retainPayable(assembler.fetch(
                productRepository.findScoredPublishedByIds(List.copyOf(rankById.keySet()))));
    }

    private MarketplaceItemResponse firstPayable(List<Object[]> rawRows) {
        ScoredProduct sp = retainPayable(assembler.fetch(rawRows)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
        return assembler.toResponse(sp);
    }

    private List<ScoredProduct> retainPayable(List<ScoredProduct> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Set<UUID> payable = payableSellerResolver.payableUserIds(MarketplaceItemAssembler.userIdsOf(rows));
        rows.removeIf(sp -> !payable.contains(sp.artisanUserId()));
        return rows;
    }

    private static DecisionLogResponse.Entry entry(int rank, ScoredProduct sp, boolean plus, String reason) {
        return new DecisionLogResponse.Entry(rank, sp.product().getId(), sp.product().getName(),
                sp.score() != null ? sp.score().getTotal() : null, plus, reason);
    }

    private static Comparator<ScoredProduct> comparatorForKey(String sortKey, Map<UUID, Double> rankById) {
        Comparator<ScoredProduct> newest = Comparator.comparing(
                (ScoredProduct sp) -> sp.product().getCreatedAt(),
                Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sortKey) {
            case "IRIS_DESC" -> Comparator.comparingDouble(ScoredProduct::total).reversed();
            case "PRICE_ASC" -> Comparator.comparingInt(sp -> sp.product().getPriceCents());
            case "PRICE_DESC" -> Comparator.comparingInt((ScoredProduct sp) -> sp.product().getPriceCents()).reversed();
            case "TEXT_RANK_DESC" -> Comparator
                    .comparingDouble((ScoredProduct sp) -> rankById.getOrDefault(sp.product().getId(), 0d))
                    .reversed()
                    .thenComparing(newest);
            default -> newest;
        };
    }

    private static String baseSortKey(String sort, boolean hasQuery) {
        return switch (sort == null ? "" : sort.toLowerCase(Locale.ROOT)) {
            case "iris" -> "IRIS_DESC";
            case "price-asc" -> "PRICE_ASC";
            case "price-desc" -> "PRICE_DESC";
            default -> hasQuery ? "TEXT_RANK_DESC" : "NEWEST";
        };
    }

    private static String searchReason(String q, String baseKey, Double rank, boolean boosted) {
        String perso = boosted ? " · reco perso (catégorie affinité)" : "";
        if (q == null) {
            return boosted ? "reco perso (catégorie affinité)" : "catalogue neutre";
        }
        String text = "texte « " + q + " »";
        if ("TEXT_RANK_DESC".equals(baseKey)) {
            return text + String.format(Locale.ROOT, " · pertinence %.4f", rank != null ? rank : 0d) + perso;
        }
        return text + " · tri " + sortLabel(baseKey) + perso;
    }

    private static String sortLabel(String baseKey) {
        return switch (baseKey) {
            case "IRIS_DESC" -> "score Iris";
            case "PRICE_ASC" -> "prix croissant";
            case "PRICE_DESC" -> "prix décroissant";
            default -> "nouveautés";
        };
    }

    private static Set<String> normalizeCategories(List<String> categories) {
        if (categories == null) {
            return Set.of();
        }
        return categories.stream()
                .filter(Objects::nonNull)
                .map(PublicCatalogService::lower)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private static UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    private static String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
