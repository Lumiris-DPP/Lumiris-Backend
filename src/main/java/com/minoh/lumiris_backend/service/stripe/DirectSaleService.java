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
import com.minoh.lumiris_backend.integration.stripe.StripeCalls;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Paiement du panier : vérifier, ouvrir le paiement Stripe, réserver le stock, confirmer au webhook.
@Service
@RequiredArgsConstructor
public class DirectSaleService {

    private static final Logger log = LoggerFactory.getLogger(DirectSaleService.class);

    // Statuts Stripe où le paiement n'est ni réussi ni abandonné.
    private static final Set<String> STILL_SETTLING_STATUSES = Set.of("processing", "requires_capture");
    private static final String PAID_CART_MESSAGE =
            "Ce panier vient d'être payé : retrouve ta commande dans « Mes commandes ».";

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

    // ===== Point d'entrée, appelé par MarketplaceCheckoutController =====

    // Checkout du panier : vérifie, ouvre le paiement Stripe, libère l'ancien, réserve.
    @Transactional(propagation = Propagation.NEVER)
    public PaymentIntentResponse createCartPaymentIntent(String buyerEmail, CartIntentRequest request) {
        properties.requireSecretKey();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        // Étape 1, transaction courte : le panier est relu et vérifié.
        Checkout checkout = transaction.execute(status -> prepareCheckout(buyerEmail, request));
        // Chaque étape valide sa propre transaction : un échec plus loin n'annule pas les précédentes.
        while (true) {
            // Étape 2 : le paiement Stripe de ce panier.
            Attempt attempt = transaction.execute(status -> openAttempt(checkout));
            // Étape 3 : les anciens paniers rendent leur stock.
            releaseSupersededReservations(checkout.buyer(), attempt.paymentIntentId());
            // Étape 4 : stock réservé et commandes PENDING enregistrées.
            PaymentIntentResponse response = transaction.execute(status -> finishAttempt(checkout, attempt, request.shipping()));
            // null : le paiement a été annulé entre-temps, on recommence.
            if (response != null) {
                return response;
            }
        }
    }

    // ===== Étape 1. Vérifier le panier (prix, déclinaisons, vendeurs, stock, devise, totaux) =====

    // Étape 1 : relit le panier en base et refuse ce qui ne peut pas être payé.
    private Checkout prepareCheckout(String buyerEmail, CartIntentRequest request) {
        User buyer = userRepository.getByEmail(buyerEmail);

        List<CartLine> lines = loadLines(request.items());
        Map<UUID, List<CartLine>> bySeller = lines.stream()
                .collect(Collectors.groupingBy(CartLine::sellerId, LinkedHashMap::new, Collectors.toList()));
        requireSellersPayable(bySeller);
        requireStockAvailable(buyer, lines);
        String currency = requireSingleCurrency(lines);
        CartTotals totals = totals(lines, bySeller);
        return new Checkout(buyer, lines, bySeller, totals, currency,
                idempotencyKey(buyer, lines, totals.amount(), currency));
    }

    // Charge chaque produit publié et sa déclinaison ; le prix vient de la base, jamais du client.
    private List<CartLine> loadLines(List<CartIntentRequest.Line> items) {
        return items.stream().map(line -> {
            MarketplaceProduct product = productRepository.findById(line.productId())
                    .filter(mp -> mp.getStatus() == MarketplaceProductStatus.PUBLISHED)
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
            return new CartLine(product, resolveVariant(product, line.variantId()), Math.max(1, line.quantity()));
        }).toList();
    }

    // Retrouve la déclinaison demandée, ou la seule s'il n'y en a qu'une.
    private MarketplaceProductVariant resolveVariant(MarketplaceProduct product, UUID variantId) {
        if (variantId != null) {
            MarketplaceProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Déclinaison introuvable"));

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

    // Refuse le panier si un atelier ne peut pas encore recevoir de paiement.
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

    // Vérifie le stock, en comptant ce que cet acheteur a déjà réservé.
    private void requireStockAvailable(User buyer, List<CartLine> lines) {
        Map<UUID, Integer> held = heldByBuyer(buyer, lines);
        for (CartLine line : lines) {
            int available = line.variant().getStock() + held.getOrDefault(line.variant().getId(), 0);
            if (available < line.quantity()) {
                throw new BillingValidationException(
                        "Stock insuffisant pour « " + lineLabel(line) + " » (reste " + available + ").");
            }
        }
    }

    // Quantités déjà réservées par cet acheteur dans ses commandes PENDING.
    private Map<UUID, Integer> heldByBuyer(User buyer, List<CartLine> lines) {
        Set<UUID> variantIds = lines.stream().map(line -> line.variant().getId()).collect(Collectors.toSet());
        Map<UUID, Integer> held = new HashMap<>();
        for (Object[] row : orderRepository.pendingQuantityByVariant(buyer.getId(), variantIds)) {
            held.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return held;
    }

    // Un paiement Stripe n'a qu'une devise : toutes les pièces doivent l'utiliser.
    private String requireSingleCurrency(List<CartLine> lines) {
        Set<String> currencies = lines.stream()
                .map(line -> line.product().getCurrency().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (currencies.size() != 1) {
            throw new BillingValidationException("Les pièces de ce panier ne sont pas vendues dans la même devise.");
        }
        return currencies.iterator().next();
    }

    // Articles, port (le plus cher par atelier) et commission, en centimes, sans dépassement.
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

    // Même panier dans la même minute = même clé, donc Stripe renvoie le même paiement.
    private String idempotencyKey(User buyer, List<CartLine> lines, int amount, String currency) {
        String cartSig = lines.stream()
                .map(l -> l.variant().getId() + "x" + l.quantity())
                .sorted().collect(Collectors.joining(",")) + "|" + amount + currency;
        return "checkout:" + buyer.getId() + ":"
                + UUID.nameUUIDFromBytes(cartSig.getBytes(StandardCharsets.UTF_8))
                + ":" + (System.currentTimeMillis() / 60_000);
    }

    // ===== Étape 2. Ouvrir le paiement Stripe =====

    // Étape 2 : crée le paiement Stripe ; s'il est déjà annulé, recommence avec une clé neuve.
    private Attempt openAttempt(Checkout checkout) {
        String key = checkout.idempotencyKey();
        while (true) {
            String transferGroup = "og_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            PaymentIntent intent = createIntent(checkout.buyer(), checkout.totals().amount(), checkout.currency(),
                    transferGroup, key);
            orderRepository.lockPaymentIntent(intent.getId());
            List<MarketplaceOrder> orders = orderRepository.lockByStripePaymentIntentId(intent.getId());

            String intentId = intent.getId();
            intent = StripeCalls.billed("Lecture du paiement impossible", () -> PaymentIntent.retrieve(intentId));
            requirePaymentStillPayable(intent);
            if ("canceled".equals(intent.getStatus())) {
                releaseReservations(intent.getId());
            }
            boolean cancelled = !orders.isEmpty()
                    && orders.stream().allMatch(order -> order.getStatus() == OrderStatus.CANCELLED);
            if (!cancelled && !(orders.isEmpty() && "canceled".equals(intent.getStatus()))) {
                return new Attempt(intent.getId(), transferGroup);
            }
            // Paiement annulé pour cette clé : on en dérive une neuve pour obtenir un paiement neuf.
            key = "checkout:after:" + UUID.nameUUIDFromBytes(
                    (key + ":after:" + intent.getId()).getBytes(StandardCharsets.UTF_8));
        }
    }

    // Appelle Stripe avec la clé d'idempotence : un double appel ne crée pas deux paiements.
    private PaymentIntent createIntent(User buyer, int amount, String currency,
                                       String transferGroup, String idempotencyKey) {
        try {
            RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
            return StripeCalls.billed("Préparation du paiement impossible", () ->
                    PaymentIntent.create(PaymentIntentCreateParams.builder()
                            .setAmount((long) amount)
                            .setCurrency(currency)
                            .setReceiptEmail(buyer.getEmail())
                            .setTransferGroup(transferGroup)
                            .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                            .putMetadata("order_type", "marketplace")
                            .putMetadata("buyer_user_id", buyer.getId().toString())
                            .putMetadata("transfer_group", transferGroup)
                            .build(), requestOptions));
        } catch (BillingException e) {
            if (e.getCause() instanceof InvalidRequestException invalid && "amount_too_large".equals(invalid.getCode())) {
                throw new BillingValidationException("Le montant de ce panier dépasse le maximum accepté au paiement.");
            }
            throw e;
        }
    }

    // Refuse si ce paiement est déjà encaissé ou encore en traitement.
    private void requirePaymentStillPayable(PaymentIntent intent) {
        if ("succeeded".equals(intent.getStatus())) {
            throw new BillingValidationException(PAID_CART_MESSAGE);
        }
        if (STILL_SETTLING_STATUSES.contains(intent.getStatus())) {
            throw new BillingValidationException(
                    "Le paiement de ce panier est en cours : retrouve son état dans « Mes commandes ».");
        }
    }

    // ===== Étape 3. Libérer les anciens paniers de l'acheteur =====

    // Étape 3 : règle chaque ancien paiement en attente, chacun dans sa transaction.
    private void releaseSupersededReservations(User buyer, String currentPaymentIntentId) {
        TransactionTemplate ownTransaction = new TransactionTemplate(transactionManager);
        ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (String paymentIntentId
                : orderRepository.findSupersededPendingPaymentIntents(buyer.getId(), currentPaymentIntentId)) {
            ownTransaction.executeWithoutResult(status -> settlePendingPayment(paymentIntentId));
        }
    }

    // Règle un paiement en attente : confirmé s'il est payé, stock rendu sinon (aussi OrderScheduler).
    @Transactional
    public void settlePendingPayment(String paymentIntentId) {
        PaymentIntent intent;
        try {
            intent = PaymentIntent.retrieve(paymentIntentId);
        } catch (InvalidRequestException e) {
            // Paiement inconnu chez Stripe : la réservation est libérée.
            if ("resource_missing".equals(e.getCode())) {
                releaseReservations(paymentIntentId);
            } else {
                log.warn("Statut du PaymentIntent {} illisible, réservation conservée : {}", paymentIntentId, e.getMessage());
            }
            return;
        } catch (StripeException e) {
            // Stripe injoignable : on garde la réservation et on réessaiera.
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

    // Annule le paiement chez Stripe ; en cas d'échec, la réservation est gardée.
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

    // Annule les commandes encore PENDING de ce paiement et rend leur stock.
    private void releaseReservations(String paymentIntentId) {
        for (MarketplaceOrder order : orderRepository.lockByStripePaymentIntentId(paymentIntentId)) {
            if (order.getStatus() == OrderStatus.PENDING) {
                lifecycleService.cancelAbandoned(order);
            }
        }
    }

    // ===== Étape 4. Réserver le stock et enregistrer les commandes =====

    // Étape 4 : sous verrou, réserve le stock et crée les commandes, ou met à jour l'existant.
    private PaymentIntentResponse finishAttempt(Checkout checkout, Attempt attempt,
                                                 CartIntentRequest.ShippingAddress shipping) {
        String paymentIntentId = attempt.paymentIntentId();
        // Verrou sur le paiement : deux requêtes du même panier passent l'une après l'autre.
        orderRepository.lockPaymentIntent(paymentIntentId);
        List<MarketplaceOrder> orders = orderRepository.lockByStripePaymentIntentId(paymentIntentId);
        PaymentIntent intent = StripeCalls.billed("Lecture du paiement impossible", () -> PaymentIntent.retrieve(paymentIntentId));
        requirePaymentStillPayable(intent);

        // Commandes déjà payées : on ne recrée rien.
        if (!orders.isEmpty() && orders.stream().noneMatch(order -> order.getStatus() == OrderStatus.PENDING)
                && orders.stream().anyMatch(order -> order.getStatus() != OrderStatus.CANCELLED)) {
            throw new BillingValidationException(PAID_CART_MESSAGE);
        }

        // Paiement ou commandes annulés : null fait recommencer createCartPaymentIntent.
        if ("canceled".equals(intent.getStatus()) || (!orders.isEmpty()
                && orders.stream().allMatch(order -> order.getStatus() == OrderStatus.CANCELLED))) {
            return null;
        }

        // Premier passage : réserve et crée ; sinon met seulement à jour l'adresse.
        if (orders.isEmpty()) {
            reserveStock(checkout.lines());
            persistOrders(checkout.buyer(), checkout.bySeller(), checkout.totals().shippingBySeller(), paymentIntentId,
                    attempt.transferGroup(), shipping);
        } else {
            refreshPendingOrders(orders, shipping);
        }

        CartTotals totals = checkout.totals();
        return new PaymentIntentResponse(
                intent.getClientSecret(), properties.publishableKey(),
                totals.amount(), totals.items(), totals.shipping(), totals.commission(),
                shipments(checkout.bySeller(), totals.shippingBySeller()));
    }

    // Baisse le stock en SQL conditionnel ; l'ordre fixe des variantes évite les interblocages.
    private void reserveStock(List<CartLine> lines) {
        List<CartLine> byVariantId = lines.stream()
                .sorted(Comparator.comparing(line -> line.variant().getId().toString()))
                .toList();
        for (CartLine line : byVariantId) {
            if (variantRepository.decrementStock(line.variant().getId(), line.quantity()) == 0) {
                throw new BillingValidationException("Stock insuffisant pour « " + lineLabel(line) + " ».");
            }
        }
    }

    // Crée une commande PENDING par ligne ; le port va sur la première ligne de chaque atelier.
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

    // Panier déjà enregistré : met seulement à jour l'adresse des commandes PENDING.
    private void refreshPendingOrders(List<MarketplaceOrder> orders, CartIntentRequest.ShippingAddress shipping) {
        List<MarketplaceOrder> pending = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .toList();
        if (pending.isEmpty()) {
            throw new BillingValidationException(PAID_CART_MESSAGE);
        }
        pending.forEach(order -> {
            applyShippingAddress(order, shipping);
            orderRepository.save(order);
        });
    }

    // Copie l'adresse de livraison sur la commande (pays par défaut : FR).
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

    // Résumé par atelier pour l'écran : nombre de pièces, port et délai de préparation.
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

    // ===== Après le paiement, appelé par StripeWebhookService =====

    // Webhook payment_intent.succeeded : passe les commandes PENDING en PAID, une seule fois.
    @Transactional
    public void fulfillByPaymentIntent(String paymentIntentId) {
        // Verrou : deux webhooks identiques ne confirment pas deux fois.
        List<MarketplaceOrder> orders = orderRepository.lockByStripePaymentIntentId(paymentIntentId);
        int confirmed = 0;
        for (MarketplaceOrder order : orders) {
            if (order.getStatus() == OrderStatus.CANCELLED && order.getStripeRefundId() == null) {
                // Payé alors que la commande est annulée : on journalise pour rembourser.
                log.error("PaymentIntent {} encaissé pour la commande annulée {} : remboursement à traiter",
                        paymentIntentId, order.getId());
                continue;
            }
            // Déjà PAID : rien à refaire (idempotence).
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

    // Crée la pièce dans la garde-robe de l'acheteur, avec facture et fin de garantie.
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

    // ===== Types internes =====

    // Nom affiché d'une ligne (produit + taille) pour les messages d'erreur.
    private String lineLabel(CartLine line) {
        String variantLabel = variantMapper.label(line.variant());
        return line.product().getName() + (variantLabel != null ? " (" + variantLabel + ")" : "");
    }

    // Le panier vérifié : acheteur, lignes, lignes par atelier, totaux, devise, clé.
    private record Checkout(User buyer, List<CartLine> lines, Map<UUID, List<CartLine>> bySeller,
                            CartTotals totals, String currency, String idempotencyKey) {}

    // Le paiement Stripe ouvert pour ce panier.
    private record Attempt(String paymentIntentId, String transferGroup) {}

    // Les montants du panier, en centimes.
    private record CartTotals(int items, int shipping, int amount, int commission,
                              Map<UUID, Integer> shippingBySeller) {}

    // Une ligne du panier : produit, déclinaison, quantité.
    private record CartLine(MarketplaceProduct product, MarketplaceProductVariant variant, int quantity) {
        // Multiplie le prix par la quantité sans dépassement silencieux.
        int lineTotal() {
            return Math.multiplyExact(product.getPriceCents(), quantity);
        }

        // Retrouve le compte vendeur de la pièce commandée.
        UUID sellerId() {
            return product.getArtisanProfile().getUser().getId();
        }

        // Retrouve le nom de l'atelier vendant la pièce.
        String sellerName() {
            return product.getArtisanProfile().getDisplayName();
        }

    }
}
