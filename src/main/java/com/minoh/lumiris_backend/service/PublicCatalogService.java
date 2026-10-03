package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.SuggestRequest;
import com.minoh.lumiris_backend.dto.out.DecisionLogResponse;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.dto.out.SearchResponse;
import com.minoh.lumiris_backend.dto.out.SuggestionResponse;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.MarketplaceProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

// Catalogue public (acheteur, sans compte) : fiches, panier, recherche filtrée et suggestions sur un
// passeport scanné. Deux invariants gravés :
//   1. Le score comparable est TOUJOURS le score Iris du DPP lié (jamais dénormalisé).
//   2. Le tri ne dépend JAMAIS de la commission — chaque tri produit un log de décision.
// Un acheteur ne voit que des pièces réellement achetables (atelier encaissable et abonné).
@Service
@RequiredArgsConstructor
public class PublicCatalogService {

    private static final int SUGGESTION_COUNT = 3;
    // La requête texte vient d'un endpoint public non authentifié et atterrit telle quelle dans une
    // ligne de log de décision append-only non prunable : on la borne.
    private static final int MAX_QUERY_LENGTH = 200;

    private final MarketplaceProductRepository productRepository;
    private final MarketplaceItemAssembler assembler;
    private final PayableSellerResolver payableSellerResolver;
    private final AtelierPlusResolver atelierPlusResolver;
    private final MarketplaceDecisionLogService decisionLogService;

    // Vue d'une fiche produit (VISION) — incrément fire-and-forget du compteur (stats vendeur).
    @Transactional
    public void trackView(UUID productId) {
        productRepository.incrementViews(productId);
    }

    // Fiche produit publiée unitaire (VISION deep-link) — 404 si non publiée ou vendeur non
    // encaissable (invariant : un acheteur ne voit que des produits réellement achetables).
    @Transactional(readOnly = true)
    public MarketplaceItemResponse getPublished(UUID id) {
        return firstPayable(productRepository.findScoredPublishedById(id));
    }

    // Fiches d'un panier, en une requête. Renvoie SEULEMENT celles encore achetables : le front
    // compare aux identifiants demandés pour signaler nommément ce qui a disparu, au lieu d'afficher
    // un compteur anonyme d'articles « retirés ».
    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> getPublishedByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return assembler.toResponses(retainPayable(assembler.fetch(
                productRepository.findScoredPublishedByIds(ids))));
    }

    // Pont scan → achat : produit publié (et achetable) lié à un passeport scanné, ou 404.
    @Transactional(readOnly = true)
    public MarketplaceItemResponse getPublishedByDpp(UUID dppFormId) {
        return firstPayable(productRepository.findScoredPublishedByDpp(dppFormId));
    }

    // Recherche publique : texte et filtres combinables, tri neutre par défaut, boost stable des
    // catégories d'affinité, et décision de tri journalisée.
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
            // Boost stable : les catégories d'affinité remontent, l'ordre neutre est préservé.
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

    // Moteur de suggestions : DPP scanné → jusqu'à trois alternatives de score au moins égal.
    @Transactional(readOnly = true)
    public SuggestionResponse suggest(SuggestRequest req) {
        double minTotal = req.score();
        String category = blankToNull(req.category());

        List<ScoredProduct> rows = retainPayable(assembler.fetch(
                productRepository.suggestCandidates(minTotal, category)));
        boolean relaxed = false;
        if (rows.size() < SUGGESTION_COUNT && category != null) {
            // Fallback : élargir hors catégorie pour TENDRE vers 3 suggestions. Le seuil de score
            // n'est JAMAIS abaissé (invariant "score >= scan") : s'il existe globalement moins de 3
            // pièces au-dessus du score scanné, on en renvoie moins (voire 0 pour un scan très élevé).
            rows = retainPayable(assembler.fetch(productRepository.suggestCandidates(minTotal, null)));
            relaxed = true;
        }

        Set<UUID> plusIds = atelierPlusResolver.atelierPlusUserIds(MarketplaceItemAssembler.userIdsOf(rows));
        // Tri exigé par le ticket : score DÉCROISSANT, puis statut ATELIER+, puis récence.
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

    // Catalogue publié, filtré sans recherche texte.
    private List<ScoredProduct> catalogueRows(String category, String material, String origin) {
        return retainPayable(assembler.fetch(productRepository.searchPublished(
                blankToNull(category), blankToNull(material), blankToNull(origin))));
    }

    // Pertinence plein texte de chaque annonce publiée qui répond à la requête et aux filtres.
    private Map<UUID, Double> textRanks(String q, String category, String material, String origin) {
        Map<UUID, Double> ranks = new LinkedHashMap<>();
        for (Object[] row : productRepository.searchPublishedTextRanked(
                q, blankToNull(category), blankToNull(material), blankToNull(origin))) {
            ranks.put(toUuid(row[0]), ((Number) row[1]).doubleValue());
        }
        return ranks;
    }

    // Hydratation par la requête du panier, qui porte déjà le join fetch de l'atelier et la
    // jointure au score. Une liste d'identifiants vide rendrait un `in ()` invalide — c'est le
    // chemin « aucun résultat », le plus fréquent.
    private List<ScoredProduct> textRows(Map<UUID, Double> rankById) {
        if (rankById.isEmpty()) {
            return new ArrayList<>();
        }
        return retainPayable(assembler.fetch(
                productRepository.findScoredPublishedByIds(List.copyOf(rankById.keySet()))));
    }

    // Première fiche achetable d'un résultat, sinon 404.
    private MarketplaceItemResponse firstPayable(List<Object[]> rawRows) {
        ScoredProduct sp = retainPayable(assembler.fetch(rawRows)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
        return assembler.toResponse(sp);
    }

    // Ne garde que les produits dont l'atelier est ENCAISSABLE (Stripe Connect actif) et abonné. Un
    // produit publié par un artisan pas encore payable reste invisible côté acheteur (il le voit,
    // lui, dans son catalogue) : on évite ainsi le cul-de-sac "impossible d'encaisser" au paiement.
    private List<ScoredProduct> retainPayable(List<ScoredProduct> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Set<UUID> payable = payableSellerResolver.payableUserIds(MarketplaceItemAssembler.userIdsOf(rows));
        rows.removeIf(sp -> !payable.contains(sp.artisanUserId()));
        return rows;
    }

    // Ligne du classement journalisé : rang, pièce, score, statut ATELIER+ et raison lisible.
    private static DecisionLogResponse.Entry entry(int rank, ScoredProduct sp, boolean plus, String reason) {
        return new DecisionLogResponse.Entry(rank, sp.product().getId(), sp.product().getName(),
                sp.score() != null ? sp.score().getTotal() : null, plus, reason);
    }

    // Le tri opère sur la clé canonique (calculée une fois par baseSortKey), jamais sur la
    // commission. Défaut NEUTRE : récence (newest first, nulls en dernier).
    // Le départage par récence sur la pertinence textuelle n'est pas cosmétique : deux documents de
    // même poids obtiennent souvent le même ts_rank, et deux recherches identiques journaliseraient
    // alors deux ordres différents — une piste d'audit non reproductible ressemble à une falsification.
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

    // Clé de tri canonique d'après le paramètre public : pertinence par défaut avec une requête texte,
    // récence sinon.
    private static String baseSortKey(String sort, boolean hasQuery) {
        return switch (sort == null ? "" : sort.toLowerCase(Locale.ROOT)) {
            case "iris" -> "IRIS_DESC";
            case "price-asc" -> "PRICE_ASC";
            case "price-desc" -> "PRICE_DESC";
            default -> hasQuery ? "TEXT_RANK_DESC" : "NEWEST";
        };
    }

    // Raison lisible du rang d'une pièce dans une recherche, reprise dans le log de décision.
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

    // Libellé français d'une clé de tri.
    private static String sortLabel(String baseKey) {
        return switch (baseKey) {
            case "IRIS_DESC" -> "score Iris";
            case "PRICE_ASC" -> "prix croissant";
            case "PRICE_DESC" -> "prix décroissant";
            default -> "nouveautés";
        };
    }

    // Catégories d'affinité en minuscules, sans valeur vide.
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

    // Identifiant renvoyé par la requête native, UUID ou texte selon le pilote.
    private static UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    // Texte coupé à la longueur maximale.
    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    // Paramètre vide traité comme absent.
    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    // Minuscules, chaîne vide pour une valeur absente.
    private static String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }

    // Chaîne vide pour une valeur absente, dans l'écho de la requête journalisée.
    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
