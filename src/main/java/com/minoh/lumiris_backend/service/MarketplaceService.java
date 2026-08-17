package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.dto.in.ProductVariantForm;
import com.minoh.lumiris_backend.dto.in.SizeMeasurementForm;
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
import com.minoh.lumiris_backend.mapper.MarketplaceVariantMapper;
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
    // Même raison pour la requête texte : elle vient d'un endpoint public non authentifié et
    // atterrit telle quelle dans une ligne append-only non prunable.
    private static final int MAX_QUERY_LENGTH = 200;

    private final MarketplaceProductRepository productRepository;
    private final MarketplaceProductVariantRepository variantRepository;
    private final MarketplaceSizeMeasurementRepository measurementRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final MarketplaceDecisionLogRepository decisionLogRepository;
    private final DecisionLogRecorder decisionLogRecorder;
    private final UserRepository userRepository;
    private final DppFormRepository dppFormRepository;
    private final IrisScoreRepository irisScoreRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MarketplaceProductMapper mapper;
    private final MarketplaceVariantMapper variantMapper;
    private final MarketplaceItemAssembler assembler;
    private final PayableSellerResolver payableSellerResolver;
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
        // Ventes réglées par produit (pour la colonne "Ventes" du catalogue vendeur).
        Map<UUID, Long> salesByProduct = orderRepository
                .salesCountByProduct(artisan.getId(), OrderStatus.sold())
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
        return assembler.toResponses(
                assembler.fetch(productRepository.findScoredByArtisanProfileId(artisan.getArtisanProfile().getId())),
                salesByProduct);
    }

    @Transactional(readOnly = true)
    public MarketplaceItemResponse getMine(String email, UUID id) {
        User artisan = requireArtisan(email);
        return toItem(requireOwnedProduct(id, artisan));
    }

    @Transactional
    public MarketplaceItemResponse update(String email, UUID id, UpdateProductRequest req) {
        User artisan = requireArtisan(email);
        MarketplaceProduct product = requireOwnedProduct(id, artisan);
        mapper.applyUpdate(product, req, resolveOwnedDpp(req.dppFormId(), artisan));
        assertSellablePrice(product);
        MarketplaceProduct saved = productRepository.save(product);
        Set<String> sizes = applyVariants(saved, req.variants());
        applySizeGuide(saved, req.sizeGuide(), sizes);
        return toItem(saved);
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

    @Transactional
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
        product.setExternalOrderUrl(req.externalOrderUrl());
        product.setPhotoUrl(req.photoUrl());
        product.setShippingCents(req.shippingCents() != null && req.shippingCents() >= 0 ? req.shippingCents() : 0);
        product.setReturnPolicy(req.returnPolicy());
        product.setPreparationDays(req.preparationDays() != null ? req.preparationDays() : 0);
        product.setWeightGrams(req.weightGrams() != null ? Math.max(0, req.weightGrams()) : 0);
        product.setStatus(req.status() != null ? req.status() : MarketplaceProductStatus.PUBLISHED);
        assertSellablePrice(product);
        MarketplaceProduct saved = productRepository.save(product);

        if (req.variants() != null && !req.variants().isEmpty()) {
            Set<String> sizes = applyVariants(saved, req.variants());
            applySizeGuide(saved, req.sizeGuide(), sizes);
        } else {
            seedDefaultVariant(saved, req.stock() != null ? req.stock() : dpp.getQuantity());
        }

        // Vente directe in-app : un seul produit/prix Stripe par annonce (dédup idempotente).
        marketplaceStripeService.ensureStripeProduct(saved);
        return toItem(saved);
    }

    // ── Déclinaisons et guide des mesures ───────────────────────────────────

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

    // Une annonce convertie sans grille de déclinaisons garde le comportement d'origine : une seule
    // déclinaison sans libellé, qui porte tout le stock et n'affiche aucun sélecteur à l'acheteur.
    private void seedDefaultVariant(MarketplaceProduct product, int stock) {
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
            if (!seen.add(sizeLabel + " " + label.toLowerCase(Locale.ROOT))) {
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
            String key = lower(form.sizeLabel()) + " " + lower(form.colorLabel());
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

    // ── Recherche publique (filtres combinables + reco perso) ───────────────

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
        DecisionLogResponse log = recordDecision("SEARCH", sortKey, requestEcho, capForLog(ranked));
        return new SearchResponse(items, log);
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

    private MarketplaceItemResponse firstPayable(List<Object[]> rawRows) {
        ScoredProduct sp = retainPayable(assembler.fetch(rawRows)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
        return assembler.toResponse(sp);
    }

    // ── Moteur de suggestions (DPP scanné → 3 alternatives) ─────────────────

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

    // Cas unitaire (create/getMine/update) : le score n'est pas déjà joint, on le charge
    // depuis le DPP lié. Les chemins de liste (listMine/search/suggest) ont déjà le score.
    private MarketplaceItemResponse toItem(MarketplaceProduct p) {
        IrisScore score = p.getDppForm() != null
                ? irisScoreRepository.findByDppFormId(p.getDppForm().getId()).orElse(null)
                : null;
        return assembler.toResponse(new ScoredProduct(p, score));
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
    // Le départage par récence sur la pertinence textuelle n'est pas cosmétique : deux documents de
    // même poids obtiennent souvent le même ts_rank, et deux recherches identiques journaliseraient
    // alors deux ordres différents — une piste d'audit non reproductible ressemble à une falsification.
    private Comparator<ScoredProduct> comparatorForKey(String sortKey, Map<UUID, Double> rankById) {
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

    private static UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
