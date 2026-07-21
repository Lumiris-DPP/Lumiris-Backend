package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.out.OrderGroupResponse;
import com.minoh.lumiris_backend.dto.out.OrderResponse;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.*;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// LUMIRIS-22 · Achat direct in-app par PaymentIntent, confirmé via Payment Element EMBARQUÉ dans l'UI
// VISION (pas de redirection). Modèle ESCROW (separate charges & transfers) : le paiement est encaissé
// sur le compte PLATEFORME (fonds retenus), puis reversé au compte connecté de l'artisan par un Transfer
// déclenché à l'expédition (cf. SellerPayoutService). Commission ~5% conservée par la plateforme.
// Un seul vendeur/panier (les transferts ciblent un unique compte connecté).
@Service
@RequiredArgsConstructor
public class DirectSaleService {

    private static final Logger log = LoggerFactory.getLogger(DirectSaleService.class);
    private static final double COMMISSION_RATE = 0.05;

    private final StripeProperties properties;
    private final MarketplaceProductRepository productRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;

    // Crée le PaymentIntent du panier (une commande PENDING par ligne, même PaymentIntent) et renvoie
    // le client secret pour le Payment Element. Le fulfillment (Garde-Robe + facture) se fait au webhook.
    @Transactional
    public PaymentIntentResponse createCartPaymentIntent(String buyerEmail, List<CartIntentRequest.Line> items) {
        properties.requireSecretKey();
        User buyer = userRepository.getByEmail(buyerEmail);

        // Charge les produits (publiés) du panier.
        List<CartLine> lines = items.stream().map(line -> {
            MarketplaceProduct p = productRepository.findById(line.productId())
                    .filter(mp -> mp.getStatus() == MarketplaceProductStatus.PUBLISHED)
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
            return new CartLine(p, Math.max(1, line.quantity()));
        }).toList();

        // Un seul vendeur par panier (le reversement cible un unique compte connecté).
        Set<UUID> sellerIds = lines.stream()
                .map(l -> l.product().getArtisanProfile().getUser().getId())
                .collect(Collectors.toSet());
        if (sellerIds.size() != 1) {
            throw new BillingValidationException(
                    "Un panier ne peut contenir que des pièces d'un même atelier — réglez chaque atelier séparément.");
        }
        User seller = lines.get(0).product().getArtisanProfile().getUser();
        // Le vendeur doit être onboardé (le reversement échouerait sinon) — validé à l'encaissement.
        sellerAccountRepository.findByUser_Id(seller.getId())
                .filter(SellerAccount::isChargesEnabled)
                .orElseThrow(() -> new BillingValidationException(
                        "Le vendeur n'a pas encore activé les paiements (Stripe Connect)."));

        // Disponibilité : refus rapide (422, avant tout appel Stripe) si le stock ne couvre pas la ligne.
        for (CartLine line : lines) {
            if (line.product().getStock() < line.quantity()) {
                throw new BillingValidationException(
                        "Stock insuffisant pour « " + line.product().getName() + " » (reste "
                                + line.product().getStock() + ").");
            }
        }

        int itemsTotal = lines.stream().mapToInt(l -> l.product().getPriceCents() * l.quantity()).sum();
        int shipping = lines.stream().mapToInt(l -> l.product().getShippingCents()).max().orElse(0);
        int commission = (int) Math.round(itemsTotal * COMMISSION_RATE);
        int amount = itemsTotal + shipping;
        String currency = lines.get(0).product().getCurrency().toLowerCase();
        // Relie la charge (encaissée par la plateforme) au(x) transfert(s) vendeur créé(s) plus tard.
        String transferGroup = "og_" + UUID.randomUUID();

        // Idempotence : un double-clic / retry réseau dans une même fenêtre courte réutilise le MÊME
        // PaymentIntent (au lieu d'en créer un nouveau + des commandes PENDING orphelines). Clé = acheteur
        // + signature du panier + bucket d'une minute (un ré-achat ultérieur du même panier obtient une
        // nouvelle clé, la clé Stripe expirant de toute façon sous 24 h).
        String cartSig = lines.stream()
                .map(l -> l.product().getId() + "x" + l.quantity())
                .sorted().collect(Collectors.joining(","));
        String idempotencyKey = "checkout:" + buyer.getId() + ":"
                + Integer.toHexString(cartSig.hashCode()) + ":" + (System.currentTimeMillis() / 60_000);
        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();

        // Escrow : charge sur le compte PLATEFORME (ni on_behalf_of, ni transfer_data, ni application_fee).
        // Les fonds sont retenus jusqu'au reversement (Transfer) déclenché à l'expédition.
        PaymentIntent intent = StripeCalls.billed("Préparation du paiement impossible", () ->
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                        .setAmount((long) amount)
                        .setCurrency(currency)
                        // Reçu Stripe envoyé automatiquement à l'acheteur (preuve d'achat + email de confirmation).
                        .setReceiptEmail(buyer.getEmail())
                        .setTransferGroup(transferGroup)
                        // Moyens de paiement : UNIQUEMENT carte bancaire + Klarna (pas de wallets
                        // Apple Pay / Google Pay — choix produit). Types explicites = wallets désactivés.
                        .addPaymentMethodType("card")
                        .addPaymentMethodType("klarna")
                        .putMetadata("order_type", "marketplace")
                        .putMetadata("buyer_user_id", buyer.getId().toString())
                        .putMetadata("transfer_group", transferGroup)
                        .build(), requestOptions));

        // Retry dans la fenêtre d'idempotence : Stripe renvoie le même PaymentIntent → si les commandes
        // existent déjà, on ne re-réserve pas le stock et on ne recrée pas les lignes.
        if (!orderRepository.findByStripePaymentIntentId(intent.getId()).isEmpty()) {
            return new PaymentIntentResponse(intent.getClientSecret(), properties.publishableKey(), amount, commission);
        }

        // Réservation atomique du stock (anti-survente sous concurrence) : décrément conditionnel par
        // ligne. Un échec (course perdue) annule toute la transaction. NB : le stock reste réservé tant
        // que le paiement n'est pas abandonné/annulé — la restitution est gérée par le cycle de vie
        // de commande (ticket dédié post-paiement).
        for (CartLine line : lines) {
            if (productRepository.decrementStock(line.product().getId(), line.quantity()) == 0) {
                throw new BillingValidationException(
                        "Stock insuffisant pour « " + line.product().getName() + " ».");
            }
        }

        // Une commande PENDING par ligne, rattachée au même PaymentIntent (fulfillment groupé au webhook).
        // Le port (unique par panier) est reversé au vendeur : on l'ajoute au net de la 1re ligne pour que
        // la plateforme ne conserve QUE la commission (économie identique à l'ancien destination charge).
        boolean firstLine = true;
        for (CartLine line : lines) {
            MarketplaceProduct p = line.product();
            int lineTotal = p.getPriceCents() * line.quantity();
            int lineCommission = (int) Math.round(lineTotal * COMMISSION_RATE);
            int lineNet = lineTotal - lineCommission + (firstLine ? shipping : 0);
            MarketplaceOrder order = new MarketplaceOrder();
            order.setProduct(p);
            order.setDppForm(p.getDppForm());
            order.setBuyer(buyer);
            order.setSeller(seller);
            order.setStripePaymentIntentId(intent.getId());
            order.setAmountTotalCents(lineTotal);
            order.setShippingCents(firstLine ? shipping : 0);
            order.setCommissionCents(lineCommission);
            order.setNetCents(lineNet);
            order.setTransferGroup(transferGroup);
            order.setCurrency(p.getCurrency());
            order.setStatus(OrderStatus.PENDING);
            orderRepository.save(order);
            firstLine = false;
        }

        return new PaymentIntentResponse(intent.getClientSecret(), properties.publishableKey(), amount, commission);
    }

    // Webhook payment_intent.succeeded (order_type=marketplace) : marque les commandes payées et ajoute
    // chaque pièce à la Garde-Robe de l'acheteur avec facture + garantie (reprise du DPP). Idempotent.
    @Transactional
    public void fulfillByPaymentIntent(String paymentIntentId) {
        List<MarketplaceOrder> orders = orderRepository.findByStripePaymentIntentId(paymentIntentId);
        for (MarketplaceOrder order : orders) {
            if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.FULFILLED) {
                continue;
            }
            order.setStatus(OrderStatus.PAID);
            String invoiceNumber = "INV-" + order.getId().toString().substring(0, 8).toUpperCase();
            order.setInvoiceNumber(invoiceNumber);
            orderRepository.save(order);

            if (order.getBuyer() != null && !wardrobeItemRepository.existsByOrder_Id(order.getId())) {
                WardrobeItem item = new WardrobeItem();
                item.setUser(order.getBuyer());
                item.setDppForm(order.getDppForm());
                item.setOrder(order);
                item.setInvoiceNumber(invoiceNumber);
                item.setWarrantyDescription(
                        order.getDppForm() != null ? order.getDppForm().getWarrantyDescription() : null);
                wardrobeItemRepository.save(item);
            }
        }
        if (!orders.isEmpty()) {
            log.info("PaymentIntent {} → {} order(s) PAID + added to wardrobe", paymentIntentId, orders.size());
        }
    }

    @Transactional(readOnly = true)
    public List<WardrobeItemResponse> getWardrobe(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return wardrobeItemRepository.findByUser_IdOrderByAcquiredAtDesc(user.getId())
                .stream().map(WardrobeItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return orderRepository.findByBuyer_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(OrderResponse::from).toList();
    }

    // Commande unitaire de l'acheteur (confirmation) — 404 si absente ou non possédée (pas de fuite).
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(String userEmail, UUID orderId) {
        User user = userRepository.getByEmail(userEmail);
        return orderRepository.findById(orderId)
                .filter(o -> o.getBuyer() != null && o.getBuyer().getId().equals(user.getId()))
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
    }

    // Groupe de commande (écran de confirmation) = toutes les lignes d'un PaymentIntent de l'acheteur.
    // Renvoie le montant EXACT débité (articles + livraison). 404 si le PI n'appartient pas à l'acheteur.
    @Transactional(readOnly = true)
    public OrderGroupResponse getMyOrderGroup(String userEmail, String paymentIntentId) {
        User user = userRepository.getByEmail(userEmail);
        List<MarketplaceOrder> orders = orderRepository.findByStripePaymentIntentId(paymentIntentId).stream()
                .filter(o -> o.getBuyer() != null && o.getBuyer().getId().equals(user.getId()))
                .toList();
        if (orders.isEmpty()) {
            throw new ResourceNotFoundException("Commande introuvable");
        }
        return OrderGroupResponse.from(paymentIntentId, orders);
    }

    private record CartLine(MarketplaceProduct product, int quantity) {}
}
