package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.dto.in.SuggestRequest;
import com.minoh.lumiris_backend.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.dto.out.DecisionLogResponse;
import com.minoh.lumiris_backend.dto.out.MarketplaceItemResponse;
import com.minoh.lumiris_backend.dto.out.SearchResponse;
import com.minoh.lumiris_backend.dto.out.SuggestionResponse;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.mapper.MarketplaceProductMapper;
import com.minoh.lumiris_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

// LUMIRIS-9 — catalogue artisan (CRUD), recherche publique filtrée, et moteur de
// suggestions. Deux invariants gravés :
//   1. Le score comparable est TOUJOURS le score Iris du DPP lié (jamais dénormalisé).
//   2. Le tri ne dépend JAMAIS de la commission — chaque tri produit un log de décision.
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private static final int SUGGESTION_COUNT = 3;
    // Borne la taille d'une ligne de log de décision : un SEARCH peut trier tout le catalogue,
    // et ces lignes append-only sont écrites par un endpoint public (croissance non prunable).
    private static final int MAX_DECISION_LOG_ENTRIES = 500;

    private final MarketplaceProductRepository productRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final MarketplaceDecisionLogRepository decisionLogRepository;
    private final DecisionLogRecorder decisionLogRecorder;
    private final UserRepository userRepository;
    private final DppFormRepository dppFormRepository;
    private final IrisScoreRepository irisScoreRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MarketplaceProductMapper mapper;
    private final AtelierPlusResolver atelierPlusResolver;
    private final com.minoh.lumiris_backend.service.stripe.MarketplaceStripeService marketplaceStripeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Vue d'une fiche produit (VISION) — incrément fire-and-forget du compteur (stats vendeur).
    @Transactional
    public void trackView(UUID productId) {
        productRepository.incrementViews(productId);
    }

    // ── Catalogue produit côté artisan (créé uniquement par conversion de DPP) ──

    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> listMine(String email) {
        User artisan = requireArtisan(email);
        boolean plus = atelierPlusResolver.isAtelierPlus(artisan.getId());
        // Ventes réglées par produit (pour la colonne "Ventes" du catalogue vendeur).
        Map<UUID, Long> salesByProduct = orderRepository
                .salesCountByProduct(artisan.getId(), java.util.List.of(OrderStatus.PAID, OrderStatus.FULFILLED))
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
        return fetch(productRepository.findScoredByArtisanProfileId(artisan.getArtisanProfile().getId()))
                .stream()
                .map(sp -> mapper.toResponse(sp.product(), sp.score(), plus,
                        salesByProduct.getOrDefault(sp.product().getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceItemResponse getMine(String email, UUID id) {
        User artisan = requireArtisan(email);
        MarketplaceProduct product = requireOwnedProduct(id, artisan);
        return toItem(product, atelierPlusResolver.isAtelierPlus(artisan.getId()));
    }

    @Transactional
    public MarketplaceItemResponse update(String email, UUID id, UpdateProductRequest req) {
        User artisan = requireArtisan(email);
        MarketplaceProduct product = requireOwnedProduct(id, artisan);
        mapper.applyUpdate(product, req, resolveOwnedDpp(req.dppFormId(), artisan));
        assertSellablePrice(product);
        return toItem(productRepository.save(product), atelierPlusResolver.isAtelierPlus(artisan.getId()));
    }

    // Prix Stripe minimum encaissable (~0,50 €). En-dessous, un PaymentIntent échouerait au checkout.
    private static final int MIN_SELLABLE_PRICE_CENTS = 50;

    @Transactional
    public void delete(String email, UUID id) {
        User artisan = requireArtisan(email);
        MarketplaceProduct product = requireOwnedProduct(id, artisan);
        // Garde-fou : une annonce déjà vendue ne se supprime pas (FK ON DELETE SET NULL → historique
        // d'achat + garde-robe orphelinés). On oriente vers l'archivage (retire de la vente, garde l'historique).
        if (orderRepository.existsByProduct_Id(id)) {
            throw new ConflictException(
                    "Cette annonce a déjà des commandes : archivez-la (elle sera retirée de la vente) "
                            + "plutôt que de la supprimer, pour préserver l'historique d'achat.");
        }
        productRepository.delete(product);
    }

    // Un produit PUBLISHED (donc achetable) doit avoir un prix encaissable par Stripe.
    private static void assertSellablePrice(MarketplaceProduct product) {
        if (product.getStatus() == MarketplaceProductStatus.PUBLISHED
                && product.getPriceCents() < MIN_SELLABLE_PRICE_CENTS) {
            throw new BillingValidationException(
                    "Un produit en vente doit coûter au moins 0,50 € (prix minimum encaissable).");
        }
    }

    public MarketplaceItemResponse convertFromDpp(String email, UUID dppFormId,
                                                  com.minoh.lumiris_backend.dto.in.ConvertDppRequest req) {
        User artisan = requireArtisan(email);
        // On ne peut pas mettre en vente sans un abonnement ATELIER actif (la vente est un service payant).
        requireSellingSubscription(artisan);
        DppForm dpp = resolveOwnedDpp(dppFormId, artisan);
        if (dpp == null) {
            throw new ResourceNotFoundException("DPP introuvable");
        }
        // LUMIRIS-22 : seules les pièces à passeport LUMIRIS valide sont vendables.
        if (dpp.getStatus() != DppStatus.VALID) {
            throw new BillingValidationException("Seules les pièces à passeport LUMIRIS valide sont vendables.");
        }
        MarketplaceProduct product = productRepository.findByDppFormId(dppFormId).orElseGet(MarketplaceProduct::new);
        if (product.getId() == null) {
            product.setArtisanProfile(artisan.getArtisanProfile());
            product.setDppForm(dpp);
        }
        product.setName(dpp.getProductName());
        product.setCategory(dpp.getProductCategory());
        product.setOriginCountry(dpp.getOriginCountry());
        product.setDescription(req.description() != null ? req.description() : dpp.getProductDescription());
        product.setMaterial(req.material());
        product.setPriceCents(req.priceCents());
        product.setCurrency(req.currency() != null && !req.currency().isBlank()
                ? req.currency().toUpperCase(Locale.ROOT) : "EUR");
        product.setStock(req.stock() != null ? req.stock() : dpp.getQuantity());
        product.setExternalOrderUrl(req.externalOrderUrl());
        product.setPhotoUrl(req.photoUrl());
        product.setShippingCents(req.shippingCents() != null && req.shippingCents() >= 0 ? req.shippingCents() : 0);
        product.setReturnPolicy(req.returnPolicy());
        product.setStatus(req.status() != null ? req.status() : MarketplaceProductStatus.PUBLISHED);
        assertSellablePrice(product);
        MarketplaceProduct saved = productRepository.save(product);
        // Vente directe in-app : un seul produit/prix Stripe par annonce (dédup idempotente).
        marketplaceStripeService.ensureStripeProduct(saved);
        return toItem(saved, atelierPlusResolver.isAtelierPlus(artisan.getId()));
    }

    // ── Recherche publique (filtres combinables + reco perso) ───────────────

    @Transactional(readOnly = true)
    public SearchResponse search(String category, String material, String origin,
                                 String sort, List<String> personalizeCategories) {
        List<ScoredProduct> rows = retainPayable(fetch(productRepository.searchPublished(
                blankToNull(category), blankToNull(material), blankToNull(origin))));
        Set<UUID> plusIds = atelierPlusResolver.atelierPlusUserIds(userIdsOf(rows));

        Set<String> perso = normalizeCategories(personalizeCategories);
        String baseKey = baseSortKey(sort);
        rows.sort(comparatorForKey(baseKey));
        if (!perso.isEmpty()) {
            // Boost stable : les catégories d'affinité remontent, l'ordre neutre est préservé.
            rows.sort(Comparator.comparingInt(sp -> perso.contains(lower(sp.product().getCategory())) ? 0 : 1));
        }
        String sortKey = perso.isEmpty() ? baseKey : baseKey + "+PERSONALIZED";

        List<MarketplaceItemResponse> items = new ArrayList<>();
        List<DecisionLogResponse.Entry> ranked = new ArrayList<>();
        int rank = 1;
        for (ScoredProduct sp : rows) {
            boolean plus = plusIds.contains(sp.artisanUserId());
            items.add(mapper.toResponse(sp.product(), sp.score(), plus));
            boolean boosted = !perso.isEmpty() && perso.contains(lower(sp.product().getCategory()));
            ranked.add(entry(rank++, sp, plus, boosted ? "reco perso (catégorie affinité)" : "catalogue neutre"));
        }
        DecisionLogResponse log = recordDecision(
                "SEARCH", sortKey,
                Map.of("category", nullToEmpty(category), "material", nullToEmpty(material),
                        "origin", nullToEmpty(origin), "sort", nullToEmpty(sort), "personalize", perso),
                capForLog(ranked));
        return new SearchResponse(items, log);
    }

    // Fiche produit publiée unitaire (VISION deep-link) — 404 si non publiée ou vendeur non
    // encaissable (invariant : un acheteur ne voit que des produits réellement achetables).
    @Transactional(readOnly = true)
    public MarketplaceItemResponse getPublished(UUID id) {
        return firstPayable(productRepository.findScoredPublishedById(id));
    }

    // Pont scan → achat : produit publié (et achetable) lié à un passeport scanné, ou 404.
    @Transactional(readOnly = true)
    public MarketplaceItemResponse getPublishedByDpp(UUID dppFormId) {
        return firstPayable(productRepository.findScoredPublishedByDpp(dppFormId));
    }

    private MarketplaceItemResponse firstPayable(List<Object[]> rawRows) {
        ScoredProduct sp = retainPayable(fetch(rawRows)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
        return mapper.toResponse(sp.product(), sp.score(), atelierPlusResolver.isAtelierPlus(sp.artisanUserId()));
    }

    // ── Moteur de suggestions (DPP scanné → 3 alternatives) ─────────────────

    @Transactional(readOnly = true)
    public SuggestionResponse suggest(SuggestRequest req) {
        double minTotal = req.score();
        String category = blankToNull(req.category());

        List<ScoredProduct> rows = retainPayable(fetch(productRepository.suggestCandidates(minTotal, category)));
        boolean relaxed = false;
        if (rows.size() < SUGGESTION_COUNT && category != null) {
            // Fallback : élargir hors catégorie pour TENDRE vers 3 suggestions. Le seuil de score
            // n'est JAMAIS abaissé (invariant "score >= scan") : s'il existe globalement moins de 3
            // pièces au-dessus du score scanné, on en renvoie moins (voire 0 pour un scan très élevé).
            rows = retainPayable(fetch(productRepository.suggestCandidates(minTotal, null)));
            relaxed = true;
        }

        Set<UUID> plusIds = atelierPlusResolver.atelierPlusUserIds(userIdsOf(rows));
        // Tri exigé par le ticket : score DÉCROISSANT, puis statut ATELIER+, puis récence.
        rows.sort(Comparator
                .comparingDouble(ScoredProduct::total).reversed()
                .thenComparing(sp -> plusIds.contains(sp.artisanUserId()) ? 0 : 1)
                .thenComparing(sp -> sp.product().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())));

        List<ScoredProduct> top = rows.stream().limit(SUGGESTION_COUNT).toList();
        String sortKey = relaxed ? "IRIS_DESC_THEN_ATELIER_PLUS/RELAXED_CATEGORY" : "IRIS_DESC_THEN_ATELIER_PLUS";

        List<SuggestionResponse.Suggestion> suggestions = new ArrayList<>();
        List<DecisionLogResponse.Entry> ranked = new ArrayList<>();
        int rank = 1;
        for (ScoredProduct sp : top) {
            boolean plus = plusIds.contains(sp.artisanUserId());
            String reason = String.format(Locale.ROOT, "score %.1f ≥ scan %.1f%s%s",
                    sp.total(), minTotal, plus ? " · ATELIER+" : "", relaxed ? " · catégorie élargie" : "");
            suggestions.add(new SuggestionResponse.Suggestion(mapper.toResponse(sp.product(), sp.score(), plus), rank, reason));
            ranked.add(entry(rank++, sp, plus, reason));
        }
        DecisionLogResponse log = recordDecision(
                "SUGGEST", sortKey,
                Map.of("category", nullToEmpty(req.category()), "grade", nullToEmpty(req.grade()),
                        "score", minTotal, "material", nullToEmpty(req.material())),
                ranked);
        return new SuggestionResponse(suggestions, log);
    }

    // ── Log de décision exposé (audit indépendant) ──────────────────────────

    // Audit indépendant réservé aux ADMIN : la piste d'audit n'est ni publique ni rattachée à un
    // artisan (aucun ownership à vérifier), on restreint donc au rôle d'audit interne.
    @Transactional(readOnly = true)
    public DecisionLogResponse getDecisionLog(String email, UUID id) {
        requireAdmin(email);
        MarketplaceDecisionLog entity = decisionLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log de décision introuvable"));
        List<DecisionLogResponse.Entry> ranked = readEntries(entity.getResult());
        return new DecisionLogResponse(entity.getId(), entity.getContext(), entity.getSortKey(),
                entity.isCommissionConsidered(), entity.getCreatedAt(), ranked);
    }

    // ── Internes ────────────────────────────────────────────────────────────

    private record ScoredProduct(MarketplaceProduct product, IrisScore score) {
        UUID artisanUserId() {
            return product.getArtisanProfile().getUser().getId();
        }

        double total() {
            return score != null ? score.getTotal() : Double.NEGATIVE_INFINITY;
        }
    }

    private static List<ScoredProduct> fetch(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new ScoredProduct((MarketplaceProduct) r[0], (IrisScore) r[1]))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // Ne garde que les produits dont l'atelier est ENCAISSABLE (Stripe Connect actif). Un produit
    // publié par un artisan pas encore payable reste invisible côté acheteur (il le voit, lui, dans
    // son catalogue) : on évite ainsi le cul-de-sac "impossible d'encaisser" au moment du paiement.
    private List<ScoredProduct> retainPayable(List<ScoredProduct> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Set<UUID> ids = userIdsOf(rows);
        Set<UUID> payable = sellerAccountRepository.payableUserIds(ids);
        Set<UUID> subscribed = activeSubscriberIds(ids);
        // Achetable seulement si le vendeur est encaissable (Stripe Connect) ET a un abonnement ATELIER
        // actif : un artisan qui laisse son abonnement expirer voit ses produits retirés de la vente.
        rows.removeIf(sp -> !payable.contains(sp.artisanUserId()) || !subscribed.contains(sp.artisanUserId()));
        return rows;
    }

    // Utilisateurs (parmi ids) ayant un abonnement ATELIER actif (source de vérité = table subscriptions).
    private Set<UUID> activeSubscriberIds(Set<UUID> ids) {
        return subscriptionRepository.findByUserIdIn(ids).stream()
                .filter(UserSubscription::isActive)
                .map(s -> s.getUser().getId())
                .collect(Collectors.toSet());
    }

    // La mise en vente exige un abonnement ATELIER actif.
    private void requireSellingSubscription(User artisan) {
        boolean active = subscriptionRepository.findByUserId(artisan.getId())
                .filter(UserSubscription::isActive)
                .isPresent();
        if (!active) {
            throw new BillingValidationException(
                    "Un abonnement ATELIER actif est requis pour mettre une pièce en vente.");
        }
    }

    private static Set<UUID> userIdsOf(List<ScoredProduct> rows) {
        return rows.stream().map(ScoredProduct::artisanUserId).collect(Collectors.toSet());
    }

    // Cas unitaire (create/getMine/update) : le score n'est pas déjà joint, on le charge
    // depuis le DPP lié. Les chemins de liste (listMine/search/suggest) ont déjà le score
    // et appellent directement mapper.toResponse.
    private MarketplaceItemResponse toItem(MarketplaceProduct p, boolean atelierPlus) {
        IrisScore score = p.getDppForm() != null
                ? irisScoreRepository.findByDppFormId(p.getDppForm().getId()).orElse(null)
                : null;
        return mapper.toResponse(p, score, atelierPlus);
    }

    private DecisionLogResponse.Entry entry(int rank, ScoredProduct sp, boolean plus, String reason) {
        return new DecisionLogResponse.Entry(rank, sp.product().getId(), sp.product().getName(),
                sp.score() != null ? sp.score().getTotal() : null, plus, reason);
    }

    // Best-effort : la piste d'audit est persistée dans une transaction séparée (REQUIRES_NEW),
    // un échec d'écriture ne doit JAMAIS faire échouer ni rollback la lecture (search/suggest).
    // En cas d'échec on renvoie un log transitoire non persisté (id null) plutôt qu'une 500.
    private DecisionLogResponse recordDecision(String context, String sortKey,
                                               Object requestEcho, List<DecisionLogResponse.Entry> ranked) {
        try {
            MarketplaceDecisionLog saved = decisionLogRecorder.persist(
                    context, sortKey, writeJson(requestEcho), writeJson(ranked));
            return new DecisionLogResponse(saved.getId(), context, sortKey,
                    saved.isCommissionConsidered(), saved.getCreatedAt(), ranked);
        } catch (RuntimeException e) {
            log.warn("Persistance du log de décision {} échouée ; log transitoire renvoyé", context, e);
            return new DecisionLogResponse(null, context, sortKey, false, Instant.now(), ranked);
        }
    }

    // Borne le nombre d'entrées écrites dans le log (taille de ligne JSONB append-only, non prunable).
    private static List<DecisionLogResponse.Entry> capForLog(List<DecisionLogResponse.Entry> ranked) {
        return ranked.size() <= MAX_DECISION_LOG_ENTRIES
                ? ranked
                : ranked.subList(0, MAX_DECISION_LOG_ENTRIES);
    }

    private User requireArtisan(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ARTISAN || user.getArtisanProfile() == null) {
            throw new RoleNotAllowedException("Un profil artisan est requis pour gérer un catalogue produit.");
        }
        return user;
    }

    private User requireAdmin(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ADMIN) {
            throw new RoleNotAllowedException("Consultation réservée à l'audit interne (ADMIN).");
        }
        return user;
    }

    private MarketplaceProduct requireOwnedProduct(UUID id, User artisan) {
        return productRepository.findByIdAndArtisanProfileId(id, artisan.getArtisanProfile().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
    }

    // Un produit ne peut lier qu'un DPP appartenant à l'artisan (sinon 404, sans fuite d'existence).
    private DppForm resolveOwnedDpp(UUID dppFormId, User artisan) {
        if (dppFormId == null) {
            return null;
        }
        DppForm dpp = dppFormRepository.findById(dppFormId)
                .orElseThrow(() -> new ResourceNotFoundException("DPP introuvable"));
        if (!dpp.getUser().getId().equals(artisan.getId())) {
            throw new ResourceNotFoundException("DPP introuvable");
        }
        return dpp;
    }

    // Le tri opère sur la clé canonique (calculée une fois par baseSortKey), jamais sur la
    // commission. Défaut NEUTRE : récence (newest first, nulls en dernier).
    private Comparator<ScoredProduct> comparatorForKey(String sortKey) {
        return switch (sortKey) {
            case "IRIS_DESC" -> Comparator.comparingDouble(ScoredProduct::total).reversed();
            case "PRICE_ASC" -> Comparator.comparingInt(sp -> sp.product().getPriceCents());
            case "PRICE_DESC" -> Comparator.comparingInt((ScoredProduct sp) -> sp.product().getPriceCents()).reversed();
            default -> Comparator.comparing((ScoredProduct sp) -> sp.product().getCreatedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private static String baseSortKey(String sort) {
        return switch (sort == null ? "" : sort.toLowerCase(Locale.ROOT)) {
            case "iris" -> "IRIS_DESC";
            case "price-asc" -> "PRICE_ASC";
            case "price-desc" -> "PRICE_DESC";
            default -> "NEWEST";
        };
    }

    private static Set<String> normalizeCategories(List<String> categories) {
        if (categories == null) {
            return Set.of();
        }
        return categories.stream()
                .filter(Objects::nonNull)
                .map(MarketplaceService::lower)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation du log de décision échouée", e);
        }
    }

    private List<DecisionLogResponse.Entry> readEntries(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<DecisionLogResponse.Entry>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Lecture du log de décision échouée", e);
        }
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
