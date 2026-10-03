package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.WardrobeItem;
import com.minoh.lumiris_backend.exception.BillingException;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.MarketplaceVariantMapper;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.repository.MarketplaceProductVariantRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import com.minoh.lumiris_backend.service.OrderLifecycleService;
import com.minoh.lumiris_backend.service.PayableSellerResolver;
import com.minoh.lumiris_backend.service.PreparationDelayResolver;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// LUMIRIS-22/24 · Achat direct in-app par PaymentIntent, confirmé via Payment Element EMBARQUÉ dans
// l'UI VISION (pas de redirection). Modèle ESCROW (separate charges & transfers) : le paiement est
// encaissé sur le compte PLATEFORME (fonds retenus), puis reversé à chaque atelier par un Transfer
// déclenché à la livraison (cf. SellerPayoutService). Commission ~5% conservée par la plateforme.
//
// Le panier couvre PLUSIEURS ateliers : l'encaissement unique se répartit en un colis (et un
// reversement) par atelier. Le port est facturé une fois par atelier, puisqu'il y a un colis par
// atelier.
@Service
@RequiredArgsConstructor
public class DirectSaleService {

    private static final Logger log = LoggerFactory.getLogger(DirectSaleService.class);

    // Un PaymentIntent dans un de ces états peut encore aboutir sans l'acheteur : sa réservation
    // reste intouchable tant que Stripe n'a pas tranché.
    private static final Set<String> STILL_SETTLING_STATUSES = Set.of("processing", "requires_capture");

    private final StripeProperties properties;
    private final MarketplaceProperties marketplaceProperties;
    private final MarketplaceProductRepository productRepository;
    private final MarketplaceProductVariantRepository variantRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final OrderLifecycleService lifecycleService;
    private final PayableSellerResolver payableSellerResolver;
    private final PreparationDelayResolver preparationDelayResolver;
    private final MarketplaceVariantMapper variantMapper;
    private final PlatformTransactionManager transactionManager;

    // Crée le PaymentIntent du panier (une commande PENDING par ligne, même PaymentIntent) et renvoie
    // le client secret pour le Payment Element. Le fulfillment (Garde-Robe + facture) se fait au webhook.
    // L'appel Stripe a lieu dans la transaction : si la suite échoue, la base est annulée mais le
    // PaymentIntent reste créé chez Stripe, sans client secret transmis, donc jamais payé.
    @Transactional
    public PaymentIntentResponse createCartPaymentIntent(String buyerEmail, CartIntentRequest request) {
        properties.requireSecretKey();
        User buyer = userRepository.getByEmail(buyerEmail);

        List<CartLine> lines = loadLines(request.items());
        Map<UUID, List<CartLine>> bySeller = groupBySeller(lines);
        requireSellersPayable(bySeller);
        requireStockAvailable(buyer, lines);
        String currency = requireSingleCurrency(lines);
        CartTotals totals = totals(lines, bySeller);

        Attempt attempt = openAttempt(buyer, totals.amount(), currency,
                idempotencyKey(buyer, lines, totals.amount(), currency));
        String paymentIntentId = attempt.intent().getId();

        // Les tentatives précédentes de l'acheteur sont réglées avant de réserver : leur stock revient
        // au catalogue, et leur ancien écran de paiement ne peut plus encaisser.
        releaseSupersededReservations(buyer, paymentIntentId);

        // Retry dans la fenêtre d'idempotence : Stripe renvoie le même PaymentIntent → si les commandes
        // existent déjà, on ne re-réserve pas le stock et on ne recrée pas les lignes.
        if (attempt.orders().isEmpty()) {
            reserveStock(lines);
            persistOrders(buyer, bySeller, totals.shippingBySeller(), paymentIntentId, attempt.transferGroup(),
                    request.shipping());
        } else {
            refreshPendingOrders(attempt.orders(), request.shipping());
        }

        return new PaymentIntentResponse(
                attempt.intent().getClientSecret(), properties.publishableKey(),
                totals.amount(), totals.items(), totals.shipping(), totals.commission(),
                shipments(bySeller, totals.shippingBySeller()));
    }

    // Ouvre la tentative de paiement de cette clé et relit ses commandes sous verrou. Deux requêtes de
    // la même tentative (double clic) s'y sérialisent : la seconde attend que la première ait validé.
    // Une tentative dont toutes les lignes ont été annulées (remplacée puis abandonnée dans la même
    // minute) ne rend pas son client secret mort : le même panier repart sur une intention neuve,
    // dont la clé dérive de l'annulée pour rester idempotente. La chaîne s'arrête au premier
    // PaymentIntent sans lignes annulées.
    private Attempt openAttempt(User buyer, int amount, String currency, String idempotencyKey) {
        String key = idempotencyKey;
        while (true) {
            // Relie la charge (encaissée par la plateforme) aux transferts vendeur créés plus tard.
            // Dérivé de la clé : un retry renvoie à Stripe exactement les mêmes paramètres, faute de
            // quoi Stripe refuse la clé au lieu de rendre le même PaymentIntent.
            String transferGroup = "og_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            PaymentIntent intent = createIntent(buyer, amount, currency, transferGroup, key);
            orderRepository.lockPaymentIntent(intent.getId());
            List<MarketplaceOrder> orders = orderRepository.lockByStripePaymentIntentId(intent.getId());
            boolean cancelled = !orders.isEmpty()
                    && orders.stream().allMatch(order -> order.getStatus() == OrderStatus.CANCELLED);
            if (!cancelled) {
                return new Attempt(intent, transferGroup, orders);
            }
            key = key + ":after:" + intent.getId();
        }
    }

    // Retry de la même tentative : l'adresse corrigée s'applique aux seules lignes encore en attente,
    // relues sous verrou, sans écraser une confirmation du webhook. Une tentative déjà réglée n'est
    // pas rouverte : son client secret ne doit plus servir.
    private void refreshPendingOrders(List<MarketplaceOrder> orders, CartIntentRequest.ShippingAddress shipping) {
        List<MarketplaceOrder> pending = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .toList();
        if (pending.isEmpty()) {
            throw new BillingValidationException(
                    "Ce panier vient d'être payé : retrouve ta commande dans « Mes commandes ».");
        }
        pending.forEach(order -> {
            applyShippingAddress(order, shipping);
            orderRepository.save(order);
        });
    }

    // Webhook payment_intent.succeeded (order_type=marketplace) : marque les commandes payées et ajoute
    // chaque pièce à la Garde-Robe de l'acheteur avec facture + garantie (reprise du DPP). Idempotent,
    // y compris sous livraisons simultanées : les lignes sont verrouillées avant d'être relues.
    @Transactional
    public void fulfillByPaymentIntent(String paymentIntentId) {
        List<MarketplaceOrder> orders = orderRepository.lockByStripePaymentIntentId(paymentIntentId);
        int confirmed = 0;
        for (MarketplaceOrder order : orders) {
            if (order.getStatus() == OrderStatus.CANCELLED && order.getStripeRefundId() == null) {
                // L'annulation précède toujours l'abandon côté Stripe ; ce cas signale une course
                // perdue : l'acheteur a payé une pièce remise en rayon, il faut le rembourser.
                log.error("PaymentIntent {} encaissé pour la commande annulée {} : remboursement à traiter",
                        paymentIntentId, order.getId());
                continue;
            }
            if (order.getStatus() != OrderStatus.PENDING) {
                continue;
            }
            order.setStatus(OrderStatus.PAID);
            String invoiceNumber = "INV-" + order.getId().toString().substring(0, 8).toUpperCase();
            order.setInvoiceNumber(invoiceNumber);
            orderRepository.save(order);

            if (order.getBuyer() != null && !wardrobeItemRepository.existsByOrder_Id(order.getId())) {
                wardrobeItemRepository.save(wardrobeItemFor(order, invoiceNumber));
            }
            lifecycleService.markPaid(order);
            confirmed++;
        }
        if (confirmed > 0) {
            log.info("PaymentIntent {} → {} commande(s) PAID + ajoutées à la Garde-Robe", paymentIntentId, confirmed);
        }
    }

    // Règle une réservation dont le paiement n'a pas abouti, Stripe faisant foi : payé → commande
    // confirmée (webhook perdu ou retardé) ; en cours ou Stripe injoignable → réservation gardée ;
    // abandonné → PaymentIntent annulé chez Stripe AVANT la remise en rayon, pour qu'aucun ancien
    // écran ne puisse encore l'encaisser.
    @Transactional
    public void settlePendingPayment(String paymentIntentId) {
        PaymentIntent intent;
        try {
            intent = PaymentIntent.retrieve(paymentIntentId);
        } catch (InvalidRequestException e) {
            // Intent inconnu de Stripe (base recréée, changement de compte) : il n'aboutira jamais,
            // sa réservation n'a plus de raison d'être.
            if ("resource_missing".equals(e.getCode())) {
                releaseReservations(paymentIntentId);
            } else {
                log.warn("Statut du PaymentIntent {} illisible, réservation conservée : {}", paymentIntentId, e.getMessage());
            }
            return;
        } catch (StripeException e) {
            // Stripe injoignable : on garde la réservation, le balayage suivant tranchera. Une remise
            // en rayon à tort survendrait la pièce.
            log.warn("Statut du PaymentIntent {} illisible, réservation conservée : {}", paymentIntentId, e.getMessage());
            return;
        }
        String status = intent.getStatus();
        if ("succeeded".equals(status)) {
            fulfillByPaymentIntent(paymentIntentId);
            return;
        }
        if (STILL_SETTLING_STATUSES.contains(status)) {
            return;
        }
        if ("canceled".equals(status) || cancelAtStripe(intent)) {
            releaseReservations(paymentIntentId);
        }
    }

    // Annule l'intention chez Stripe. Un refus (paiement abouti entre la lecture et l'annulation) ou
    // une panne garde la réservation : le webhook ou le balayage suivant confirmera ou relâchera.
    private boolean cancelAtStripe(PaymentIntent intent) {
        try {
            intent.cancel(PaymentIntentCancelParams.builder().build(),
                    RequestOptions.builder().setIdempotencyKey("cancel:" + intent.getId()).build());
            return true;
        } catch (StripeException e) {
            log.warn("Annulation du PaymentIntent {} impossible, réservation conservée : {}",
                    intent.getId(), e.getMessage());
            return false;
        }
    }

    // Remet en rayon les lignes encore en attente d'un paiement abandonné, relues sous verrou : une
    // ligne confirmée entre-temps par le webhook n'est pas annulée.
    private void releaseReservations(String paymentIntentId) {
        for (MarketplaceOrder order : orderRepository.lockByStripePaymentIntentId(paymentIntentId)) {
            if (order.getStatus() == OrderStatus.PENDING) {
                lifecycleService.cancelAbandoned(order);
            }
        }
    }

    // L'échéance de garantie est FIGÉE ici : l'atelier peut raccourcir la garantie de ses futures
    // pièces, pas celle déjà vendue. Sans durée déclarée sur le passeport, elle reste nulle — la
    // Garde-Robe n'alerte alors sur rien, plutôt que d'inventer une échéance sur un droit
    // contractuel.
    private WardrobeItem wardrobeItemFor(MarketplaceOrder order, String invoiceNumber) {
        DppForm dpp = order.getDppForm();
        WardrobeItem item = new WardrobeItem();
        item.setUser(order.getBuyer());
        item.setDppForm(dpp);
        item.setOrder(order);
        item.setInvoiceNumber(invoiceNumber);
        item.setWarrantyDescription(dpp != null ? dpp.getWarrantyDescription() : null);
        if (dpp != null && dpp.getWarrantyMonths() != null && dpp.getWarrantyMonths() > 0) {
            item.setWarrantyUntil(item.getAcquiredAt()
                    .atZone(ZoneOffset.UTC)
                    .plusMonths(dpp.getWarrantyMonths())
                    .toInstant());
        }
        return item;
    }

    // Charge les lignes du panier depuis le catalogue publié : le navigateur ne fournit que des
    // identifiants et des quantités, jamais un prix.
    private List<CartLine> loadLines(List<CartIntentRequest.Line> items) {
        return items.stream().map(line -> {
            MarketplaceProduct product = productRepository.findById(line.productId())
                    .filter(mp -> mp.getStatus() == MarketplaceProductStatus.PUBLISHED)
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
            return new CartLine(product, resolveVariant(product, line.variantId()), Math.max(1, line.quantity()));
        }).toList();
    }

    // Résolution de la déclinaison vendue. `variantId` absent est un cas normal : un bundle mobile
    // antérieur à la feature tourne encore depuis un cache navigateur ou un shell Tauri. On prend
    // alors la déclinaison unique de l'annonce, et on refuse en 422 si l'annonce en a plusieurs.
    private MarketplaceProductVariant resolveVariant(MarketplaceProduct product, UUID variantId) {
        if (variantId != null) {
            MarketplaceProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Déclinaison introuvable"));
            // Sans ce contrôle, un panier forgé facturerait le prix d'une annonce en décrémentant
            // le stock d'une autre.
            if (!variant.getProduct().getId().equals(product.getId())) {
                throw new ResourceNotFoundException("Déclinaison introuvable");
            }
            return variant;
        }
        List<MarketplaceProductVariant> variants =
                variantRepository.findByProduct_IdOrderByPositionAscIdAsc(product.getId());
        if (variants.size() == 1) {
            return variants.get(0);
        }
        throw new BillingValidationException(
                "Choisis une taille pour « " + product.getName() + " » avant de payer.");
    }

    // Regroupe les lignes par atelier, ordre d'insertion préservé : les colis du récapitulatif
    // suivent l'ordre du panier.
    private Map<UUID, List<CartLine>> groupBySeller(List<CartLine> lines) {
        Map<UUID, List<CartLine>> bySeller = new LinkedHashMap<>();
        for (CartLine line : lines) {
            bySeller.computeIfAbsent(line.sellerId(), key -> new ArrayList<>()).add(line);
        }
        return bySeller;
    }

    // Tous les ateliers du panier doivent être payables selon la même règle que le catalogue public
    // (Connect encaissable ET abonnement actif) : une requête forgée ne doit pas acheter ce que la
    // Boutique n'affiche plus, et le reversement échouerait sinon après encaissement.
    private void requireSellersPayable(Map<UUID, List<CartLine>> bySeller) {
        Set<UUID> payable = payableSellerResolver.payableUserIds(bySeller.keySet());
        for (Map.Entry<UUID, List<CartLine>> entry : bySeller.entrySet()) {
            if (!payable.contains(entry.getKey())) {
                throw new BillingValidationException(
                        "L'atelier « " + entry.getValue().get(0).sellerName()
                                + " » n'a pas encore activé les paiements — retire ses pièces du panier.");
            }
        }
    }

    // Refus rapide (422, avant tout appel Stripe) si le stock ne couvre pas une ligne. Les réservations
    // en attente de CET acheteur comptent comme disponibles : un retry de la même tentative les
    // réutilise, une nouvelle tentative les relâche avant de réserver.
    private void requireStockAvailable(User buyer, List<CartLine> lines) {
        Map<UUID, Integer> held = heldByBuyer(buyer, lines);
        for (CartLine line : lines) {
            int available = line.variant().getStock() + held.getOrDefault(line.variant().getId(), 0);
            if (available < line.quantity()) {
                throw new BillingValidationException(
                        "Stock insuffisant pour « " + line.label() + " » (reste " + available + ").");
            }
        }
    }

    // Quantités que l'acheteur tient déjà en réserve sur les déclinaisons du panier.
    private Map<UUID, Integer> heldByBuyer(User buyer, List<CartLine> lines) {
        Set<UUID> variantIds = lines.stream().map(line -> line.variant().getId()).collect(Collectors.toSet());
        Map<UUID, Integer> held = new HashMap<>();
        for (Object[] row : orderRepository.pendingQuantityByVariant(buyer.getId(), variantIds)) {
            held.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return held;
    }

    // Un PaymentIntent n'a qu'une devise : un panier qui en mélange plusieurs serait facturé, faux,
    // dans celle de sa première ligne.
    private String requireSingleCurrency(List<CartLine> lines) {
        Set<String> currencies = lines.stream()
                .map(line -> line.product().getCurrency().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (currencies.size() != 1) {
            throw new BillingValidationException("Les pièces de ce panier ne sont pas vendues dans la même devise.");
        }
        return currencies.iterator().next();
    }

    // Montants du panier en centimes, calculés sans dépassement possible : un total qui sort d'un
    // entier est refusé au lieu de partir faux chez Stripe. Un colis par atelier : son port est le
    // plus élevé de ses lignes (une expédition groupée coûte le port de la pièce la plus contraignante,
    // pas la somme des ports).
    private CartTotals totals(List<CartLine> lines, Map<UUID, List<CartLine>> bySeller) {
        try {
            int items = 0;
            for (CartLine line : lines) {
                items = Math.addExact(items, line.lineTotal());
            }
            Map<UUID, Integer> shippingBySeller = new LinkedHashMap<>();
            int shipping = 0;
            for (Map.Entry<UUID, List<CartLine>> entry : bySeller.entrySet()) {
                int sellerShipping = entry.getValue().stream()
                        .mapToInt(line -> line.product().getShippingCents()).max().orElse(0);
                shippingBySeller.put(entry.getKey(), sellerShipping);
                shipping = Math.addExact(shipping, sellerShipping);
            }
            int commission = (int) Math.round(items * marketplaceProperties.getCommissionRate());
            return new CartTotals(items, shipping, Math.addExact(items, shipping), commission, shippingBySeller);
        } catch (ArithmeticException e) {
            throw new BillingValidationException("Le montant de ce panier est invalide.");
        }
    }

    // Réservation atomique du stock (anti-survente sous concurrence) : décrément conditionnel par
    // ligne. Un échec (course perdue) annule toute la transaction. Les déclinaisons sont réservées par
    // identifiant croissant, l'ordre où l'atelier les verrouille en enregistrant son annonce : deux
    // transactions qui se croisent s'attendent au lieu de s'interbloquer. Le texte de l'identifiant
    // suit l'ordre des uuid de PostgreSQL, contrairement à UUID.compareTo (comparaison signée).
    private void reserveStock(List<CartLine> lines) {
        List<CartLine> byVariantId = lines.stream()
                .sorted(Comparator.comparing(line -> line.variant().getId().toString()))
                .toList();
        for (CartLine line : byVariantId) {
            if (variantRepository.decrementStock(line.variant().getId(), line.quantity()) == 0) {
                throw new BillingValidationException("Stock insuffisant pour « " + line.label() + " ».");
            }
        }
    }

    // Idempotence : un double-clic / retry réseau dans une même fenêtre courte réutilise le MÊME
    // PaymentIntent (au lieu d'en créer un nouveau + des commandes PENDING orphelines). Clé = acheteur
    // + signature du panier + bucket d'une minute (un ré-achat ultérieur du même panier obtient une
    // nouvelle clé, la clé Stripe expirant de toute façon sous 24 h). La signature porte la
    // DÉCLINAISON (deux paniers ne différant que par la taille auraient sinon la même clé), le montant
    // et la devise : un prix modifié entre deux clics est une autre opération, pas un retry.
    private String idempotencyKey(User buyer, List<CartLine> lines, int amount, String currency) {
        String cartSig = lines.stream()
                .map(l -> l.variant().getId() + "x" + l.quantity())
                .sorted().collect(Collectors.joining(",")) + "|" + amount + currency;
        return "checkout:" + buyer.getId() + ":"
                + UUID.nameUUIDFromBytes(cartSig.getBytes(StandardCharsets.UTF_8))
                + ":" + (System.currentTimeMillis() / 60_000);
    }

    // Escrow : charge sur le compte PLATEFORME (ni on_behalf_of, ni transfer_data, ni application_fee).
    // Les fonds sont retenus jusqu'au reversement (Transfer) déclenché à la livraison. Un montant que
    // Stripe refuse comme trop élevé est un refus de ce panier (422), pas une panne du paiement (502).
    private PaymentIntent createIntent(User buyer, int amount, String currency,
                                       String transferGroup, String idempotencyKey) {
        try {
            return requestIntent(buyer, amount, currency, transferGroup, idempotencyKey);
        } catch (BillingException e) {
            if (e.getCause() instanceof InvalidRequestException invalid && "amount_too_large".equals(invalid.getCode())) {
                throw new BillingValidationException("Le montant de ce panier dépasse le maximum accepté au paiement.");
            }
            throw e;
        }
    }

    // Appel Stripe de création du PaymentIntent, échecs traduits en BillingException.
    private PaymentIntent requestIntent(User buyer, int amount, String currency,
                                        String transferGroup, String idempotencyKey) {
        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
        return StripeCalls.billed("Préparation du paiement impossible", () ->
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                        .setAmount((long) amount)
                        .setCurrency(currency)
                        // Reçu Stripe envoyé automatiquement à l'acheteur (preuve d'achat + email de confirmation).
                        .setReceiptEmail(buyer.getEmail())
                        .setTransferGroup(transferGroup)
                        // Méthodes automatiques : Stripe propose toutes les méthodes éligibles activées au
                        // dashboard — carte, Klarna (paiement en plusieurs fois), et wallets Apple Pay /
                        // Google Pay (ces derniers uniquement en HTTPS + domaine vérifié → invisibles en
                        // local http). Redirections autorisées (nécessaire pour Klarna) — confirmPayment
                        // utilise redirect:'if_required', le retour est géré par return_url.
                        .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                        .putMetadata("order_type", "marketplace")
                        .putMetadata("buyer_user_id", buyer.getId().toString())
                        .putMetadata("transfer_group", transferGroup)
                        .build(), requestOptions));
    }

    // Règlement des tentatives précédentes de l'acheteur, chacune dans sa propre transaction : une
    // annulation faite chez Stripe reste acquise en base même si la réservation de ce panier échoue
    // ensuite, et une confirmation trouvée en route n'est pas défaite par ce rollback.
    private void releaseSupersededReservations(User buyer, String currentPaymentIntentId) {
        TransactionTemplate ownTransaction = new TransactionTemplate(transactionManager);
        ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (String paymentIntentId
                : orderRepository.findSupersededPendingPaymentIntents(buyer.getId(), currentPaymentIntentId)) {
            ownTransaction.executeWithoutResult(status -> settlePendingPayment(paymentIntentId));
        }
    }

    // Une commande PENDING par ligne, rattachée au même PaymentIntent. Le port d'un atelier (unique
    // pour son colis) est porté par sa première ligne et reversé au vendeur : on l'ajoute à son net
    // pour que la plateforme ne conserve QUE la commission.
    private void persistOrders(User buyer, Map<UUID, List<CartLine>> bySeller,
                               Map<UUID, Integer> shippingBySeller, String paymentIntentId,
                               String transferGroup, CartIntentRequest.ShippingAddress shipping) {
        for (Map.Entry<UUID, List<CartLine>> entry : bySeller.entrySet()) {
            int sellerShipping = shippingBySeller.getOrDefault(entry.getKey(), 0);
            boolean firstLine = true;
            for (CartLine line : entry.getValue()) {
                MarketplaceProduct p = line.product();
                int lineTotal = line.lineTotal();
                int lineCommission = (int) Math.round(lineTotal * marketplaceProperties.getCommissionRate());
                MarketplaceOrder order = new MarketplaceOrder();
                order.setProduct(p);
                order.setVariant(line.variant());
                order.setVariantLabel(variantMapper.label(line.variant()));
                order.setDppForm(p.getDppForm());
                order.setBuyer(buyer);
                order.setSeller(p.getArtisanProfile().getUser());
                order.setQuantity(line.quantity());
                order.setStripePaymentIntentId(paymentIntentId);
                order.setAmountTotalCents(lineTotal);
                order.setShippingCents(firstLine ? sellerShipping : 0);
                order.setCommissionCents(lineCommission);
                order.setNetCents(lineTotal - lineCommission + (firstLine ? sellerShipping : 0));
                order.setTransferGroup(transferGroup);
                order.setCurrency(p.getCurrency());
                order.setStatus(OrderStatus.PENDING);
                applyShippingAddress(order, shipping);
                orderRepository.save(order);
                firstLine = false;
            }
        }
    }

    // Copie l'adresse de livraison saisie au checkout sur la commande (pays FR par défaut).
    private void applyShippingAddress(MarketplaceOrder order, CartIntentRequest.ShippingAddress shipping) {
        order.setShipToName(shipping.fullName());
        order.setShipToLine1(shipping.line1());
        order.setShipToLine2(shipping.line2());
        order.setShipToPostalCode(shipping.postalCode());
        order.setShipToCity(shipping.city());
        order.setShipToCountry(shipping.country() == null || shipping.country().isBlank()
                ? "FR" : shipping.country().toUpperCase());
        order.setShipToPhone(shipping.phone());
    }

    // Sur un panier multi-atelier, le récapitulatif est le seul endroit qui peut dire « Atelier A
    // sous 3 jours, Atelier B sous 17 jours » : le délai retenu est le plus long du colis.
    private List<PaymentIntentResponse.Shipment> shipments(Map<UUID, List<CartLine>> bySeller,
                                                           Map<UUID, Integer> shippingBySeller) {
        Instant now = Instant.now();
        return bySeller.entrySet().stream()
                .map(e -> new PaymentIntentResponse.Shipment(
                        e.getValue().get(0).sellerName(),
                        e.getValue().stream().mapToInt(CartLine::quantity).sum(),
                        shippingBySeller.getOrDefault(e.getKey(), 0),
                        e.getValue().stream()
                                .mapToInt(l -> preparationDelayResolver.effectiveDays(l.product(), now))
                                .max().orElse(0)))
                .toList();
    }

    // Tentative de paiement ouverte : son PaymentIntent, son groupe de transferts et ses commandes
    // déjà créées, relues sous verrou (vide pour une tentative neuve).
    private record Attempt(PaymentIntent intent, String transferGroup, List<MarketplaceOrder> orders) {}

    // Montants du panier en centimes ; le port est détaillé par atelier pour le récapitulatif.
    private record CartTotals(int items, int shipping, int amount, int commission,
                              Map<UUID, Integer> shippingBySeller) {}

    // Une ligne du panier telle que le serveur la relit : annonce, déclinaison et quantité.
    private record CartLine(MarketplaceProduct product, MarketplaceProductVariant variant, int quantity) {

        // Prix de la ligne en centimes ; lève ArithmeticException plutôt que de déborder.
        int lineTotal() {
            return Math.multiplyExact(product.getPriceCents(), quantity);
        }

        // Utilisateur de l'atelier vendeur, clé de regroupement des colis.
        UUID sellerId() {
            return product.getArtisanProfile().getUser().getId();
        }

        // Nom public de l'atelier, affiché dans les refus et le récapitulatif.
        String sellerName() {
            return product.getArtisanProfile().getDisplayName();
        }

        // Nom de la pièce suivi de sa taille et de sa couleur quand elles existent.
        String label() {
            String sizeLabel = variant.getSizeLabel();
            String colorLabel = variant.getColorLabel();
            boolean hasSize = sizeLabel != null && !sizeLabel.isBlank();
            boolean hasColor = colorLabel != null && !colorLabel.isBlank();
            if (!hasSize && !hasColor) {
                return product.getName();
            }
            return product.getName() + " (" + (hasSize && hasColor ? sizeLabel + " · " + colorLabel
                    : hasSize ? sizeLabel : colorLabel) + ")";
        }
    }
}
