package com.minoh.lumiris_backend.marketplace.checkout.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.config.stripe.StripeProperties;
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
import com.minoh.lumiris_backend.marketplace.catalog.mapper.MarketplaceVariantMapper;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductVariantRepository;
import com.minoh.lumiris_backend.marketplace.checkout.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.marketplace.checkout.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.marketplace.order.service.OrderLifecycleService;
import com.minoh.lumiris_backend.marketplace.seller.service.PayableSellerResolver;
import com.minoh.lumiris_backend.marketplace.seller.service.PreparationDelayResolver;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import com.minoh.lumiris_backend.integration.stripe.StripeCalls;
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

// Prépare le paiement et réserve les pièces du panier.
@Service
@RequiredArgsConstructor
public class DirectSaleService {

    private static final Logger log = LoggerFactory.getLogger(DirectSaleService.class);

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

    // Prépare le paiement du panier en transactions successives.
    @Transactional(propagation = Propagation.NEVER)
    public PaymentIntentResponse createCartPaymentIntent(String buyerEmail, CartIntentRequest request) {
        properties.requireSecretKey();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Checkout checkout = transaction.execute(status -> prepareCheckout(buyerEmail, request));
        while (true) {
            Attempt attempt = transaction.execute(status -> openAttempt(checkout));

            releaseSupersededReservations(checkout.buyer(), attempt.paymentIntentId());
            PaymentIntentResponse response = transaction.execute(status -> finishAttempt(checkout, attempt, request.shipping()));
            if (response != null) {
                return response;
            }
        }
    }

    // Vérifie les pièces, les ateliers et les montants du panier.
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

    // Relit le paiement avant de réserver et adresser les commandes.
    private PaymentIntentResponse finishAttempt(Checkout checkout, Attempt attempt,
                                                 CartIntentRequest.ShippingAddress shipping) {
        String paymentIntentId = attempt.paymentIntentId();
        orderRepository.lockPaymentIntent(paymentIntentId);
        List<MarketplaceOrder> orders = orderRepository.lockByStripePaymentIntentId(paymentIntentId);
        PaymentIntent intent = StripeCalls.billed("Lecture du paiement impossible", () -> PaymentIntent.retrieve(paymentIntentId));
        requirePaymentStillPayable(intent);

        if (!orders.isEmpty() && orders.stream().noneMatch(order -> order.getStatus() == OrderStatus.PENDING)
                && orders.stream().anyMatch(order -> order.getStatus() != OrderStatus.CANCELLED)) {
            throw new BillingValidationException(PAID_CART_MESSAGE);
        }

        if ("canceled".equals(intent.getStatus()) || (!orders.isEmpty()
                && orders.stream().allMatch(order -> order.getStatus() == OrderStatus.CANCELLED))) {
            return null;
        }

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

    // Ouvre une tentative utilisable après les tentatives annulées.
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
            key = "checkout:after:" + UUID.nameUUIDFromBytes(
                    (key + ":after:" + intent.getId()).getBytes(StandardCharsets.UTF_8));
        }
    }

    // Refuse un paiement déjà encaissé ou encore en cours.
    private void requirePaymentStillPayable(PaymentIntent intent) {
        if ("succeeded".equals(intent.getStatus())) {
            throw new BillingValidationException(PAID_CART_MESSAGE);
        }
        if (STILL_SETTLING_STATUSES.contains(intent.getStatus())) {
            throw new BillingValidationException(
                    "Le paiement de ce panier est en cours : retrouve son état dans « Mes commandes ».");
        }
    }

    // Actualise uniquement les adresses des commandes encore en attente.
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

    // Confirme les commandes payées et ajoute les pièces acquises.
    @Transactional
    public void fulfillByPaymentIntent(String paymentIntentId) {
        List<MarketplaceOrder> orders = orderRepository.lockByStripePaymentIntentId(paymentIntentId);
        int confirmed = 0;
        for (MarketplaceOrder order : orders) {
            if (order.getStatus() == OrderStatus.CANCELLED && order.getStripeRefundId() == null) {

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

    // Relit le paiement avant de confirmer ou libérer sa réservation.
    @Transactional
    public void settlePendingPayment(String paymentIntentId) {
        PaymentIntent intent;
        try {
            intent = PaymentIntent.retrieve(paymentIntentId);
        } catch (InvalidRequestException e) {

            if ("resource_missing".equals(e.getCode())) {
                releaseReservations(paymentIntentId);
            } else {
                log.warn("Statut du PaymentIntent {} illisible, réservation conservée : {}", paymentIntentId, e.getMessage());
            }
            return;
        } catch (StripeException e) {

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

    // Demande l'annulation du paiement avant de libérer les pièces.
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

    // Annule les commandes encore en attente et rend leur stock.
    private void releaseReservations(String paymentIntentId) {
        for (MarketplaceOrder order : orderRepository.lockByStripePaymentIntentId(paymentIntentId)) {
            if (order.getStatus() == OrderStatus.PENDING) {
                lifecycleService.cancelAbandoned(order);
            }
        }
    }

    // Prépare la pièce acquise avec sa facture et sa garantie.
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

    // Charge les pièces publiées et leurs déclinaisons demandées.
    private List<CartLine> loadLines(List<CartIntentRequest.Line> items) {
        return items.stream().map(line -> {
            MarketplaceProduct product = productRepository.findById(line.productId())
                    .filter(mp -> mp.getStatus() == MarketplaceProductStatus.PUBLISHED)
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
            return new CartLine(product, resolveVariant(product, line.variantId()), Math.max(1, line.quantity()));
        }).toList();
    }

    // Vérifie la déclinaison choisie ou utilise l'unique déclinaison.
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

    // Refuse les ateliers qui ne peuvent pas recevoir le paiement.
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

    // Vérifie le stock en incluant les réservations de cet acheteur.
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

    // Charge les quantités déjà réservées par cet acheteur.
    private Map<UUID, Integer> heldByBuyer(User buyer, List<CartLine> lines) {
        Set<UUID> variantIds = lines.stream().map(line -> line.variant().getId()).collect(Collectors.toSet());
        Map<UUID, Integer> held = new HashMap<>();
        for (Object[] row : orderRepository.pendingQuantityByVariant(buyer.getId(), variantIds)) {
            held.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return held;
    }

    // Vérifie que toutes les pièces utilisent la même devise.
    private String requireSingleCurrency(List<CartLine> lines) {
        Set<String> currencies = lines.stream()
                .map(line -> line.product().getCurrency().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (currencies.size() != 1) {
            throw new BillingValidationException("Les pièces de ce panier ne sont pas vendues dans la même devise.");
        }
        return currencies.iterator().next();
    }

    // Calcule les montants et les frais de livraison par atelier.
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

    // Réserve les quantités en suivant l'ordre des déclinaisons.
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

    // Identifie le même panier de l'acheteur pendant la minute.
    private String idempotencyKey(User buyer, List<CartLine> lines, int amount, String currency) {
        String cartSig = lines.stream()
                .map(l -> l.variant().getId() + "x" + l.quantity())
                .sorted().collect(Collectors.joining(",")) + "|" + amount + currency;
        return "checkout:" + buyer.getId() + ":"
                + UUID.nameUUIDFromBytes(cartSig.getBytes(StandardCharsets.UTF_8))
                + ":" + (System.currentTimeMillis() / 60_000);
    }

    // Crée le paiement Stripe et traduit les erreurs de montant.
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

    // Traite séparément les anciennes réservations avant la nouvelle réservation.
    private void releaseSupersededReservations(User buyer, String currentPaymentIntentId) {
        TransactionTemplate ownTransaction = new TransactionTemplate(transactionManager);
        ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (String paymentIntentId
                : orderRepository.findSupersededPendingPaymentIntents(buyer.getId(), currentPaymentIntentId)) {
            ownTransaction.executeWithoutResult(status -> settlePendingPayment(paymentIntentId));
        }
    }

    // Enregistre les commandes avec les montants et l'adresse du panier.
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

    // Reporte les coordonnées de livraison sur la commande.
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

    // Présente les frais et délais de chaque atelier du panier.
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

    // Regroupe les pièces et les montants du panier vérifié.
    private record Checkout(User buyer, List<CartLine> lines, Map<UUID, List<CartLine>> bySeller,
                            CartTotals totals, String currency, String idempotencyKey) {}

    // Associe le nom de la pièce à sa déclinaison.
    private String lineLabel(CartLine line) {
        String variantLabel = variantMapper.label(line.variant());
        return line.product().getName() + (variantLabel != null ? " (" + variantLabel + ")" : "");
    }

    // Conserve les références du paiement ouvert pour le panier.
    private record Attempt(String paymentIntentId, String transferGroup) {}

    // Regroupe les montants du panier et les frais par atelier.
    private record CartTotals(int items, int shipping, int amount, int commission,
                              Map<UUID, Integer> shippingBySeller) {}

    // Associe une pièce à sa déclinaison et sa quantité.
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
