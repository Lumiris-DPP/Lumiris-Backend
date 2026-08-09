package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.*;
import com.minoh.lumiris_backend.service.OrderLifecycleService;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final StripeProperties properties;
    private final MarketplaceProperties marketplaceProperties;
    private final MarketplaceProductRepository productRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final OrderLifecycleService lifecycleService;

    // Crée le PaymentIntent du panier (une commande PENDING par ligne, même PaymentIntent) et renvoie
    // le client secret pour le Payment Element. Le fulfillment (Garde-Robe + facture) se fait au webhook.
    @Transactional
    public PaymentIntentResponse createCartPaymentIntent(String buyerEmail, CartIntentRequest request) {
        properties.requireSecretKey();
        User buyer = userRepository.getByEmail(buyerEmail);

        List<CartLine> lines = loadLines(request.items());
        Map<UUID, List<CartLine>> bySeller = groupBySeller(lines);
        requireSellersPayable(bySeller);
        requireStockAvailable(lines);

        int itemsTotal = lines.stream().mapToInt(CartLine::lineTotal).sum();
        // Un colis par atelier : le port de l'atelier est le plus élevé de ses lignes (une expédition
        // groupée coûte le port de la pièce la plus contraignante, pas la somme des ports).
        Map<UUID, Integer> shippingBySeller = bySeller.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().mapToInt(l -> l.product().getShippingCents()).max().orElse(0)));
        int shippingTotal = shippingBySeller.values().stream().mapToInt(Integer::intValue).sum();
        int commission = (int) Math.round(itemsTotal * marketplaceProperties.getCommissionRate());
        int amount = itemsTotal + shippingTotal;
        String currency = lines.get(0).product().getCurrency().toLowerCase();
        // Relie la charge (encaissée par la plateforme) aux transferts vendeur créés plus tard.
        String transferGroup = "og_" + UUID.randomUUID();

        PaymentIntent intent = createIntent(buyer, amount, currency, transferGroup, idempotencyKey(buyer, lines));

        // Retry dans la fenêtre d'idempotence : Stripe renvoie le même PaymentIntent → si les commandes
        // existent déjà, on ne re-réserve pas le stock et on ne recrée pas les lignes. On rafraîchit en
        // revanche l'adresse : l'acheteur a pu revenir corriger sa livraison avant de payer.
        List<MarketplaceOrder> existing = orderRepository.findByStripePaymentIntentId(intent.getId());
        if (existing.isEmpty()) {
            reserveStock(lines);
            persistOrders(buyer, bySeller, shippingBySeller, intent.getId(), transferGroup, request.shipping());
        } else {
            existing.forEach(order -> {
                applyShippingAddress(order, request.shipping());
                orderRepository.save(order);
            });
        }

        return new PaymentIntentResponse(
                intent.getClientSecret(), properties.publishableKey(),
                amount, itemsTotal, shippingTotal, commission,
                shipments(bySeller, shippingBySeller));
    }

    // Webhook payment_intent.succeeded (order_type=marketplace) : marque les commandes payées et ajoute
    // chaque pièce à la Garde-Robe de l'acheteur avec facture + garantie (reprise du DPP). Idempotent.
    @Transactional
    public void fulfillByPaymentIntent(String paymentIntentId) {
        List<MarketplaceOrder> orders = orderRepository.findByStripePaymentIntentId(paymentIntentId);
        int confirmed = 0;
        for (MarketplaceOrder order : orders) {
            if (order.getStatus() != OrderStatus.PENDING) {
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
            lifecycleService.markPaid(order);
            confirmed++;
        }
        if (confirmed > 0) {
            log.info("PaymentIntent {} → {} commande(s) PAID + ajoutées à la Garde-Robe", paymentIntentId, confirmed);
        }
    }

    private List<CartLine> loadLines(List<CartIntentRequest.Line> items) {
        return items.stream().map(line -> {
            MarketplaceProduct p = productRepository.findById(line.productId())
                    .filter(mp -> mp.getStatus() == MarketplaceProductStatus.PUBLISHED)
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable"));
            return new CartLine(p, Math.max(1, line.quantity()));
        }).toList();
    }

    // Ordre d'insertion préservé : les colis du récapitulatif suivent l'ordre du panier.
    private Map<UUID, List<CartLine>> groupBySeller(List<CartLine> lines) {
        Map<UUID, List<CartLine>> bySeller = new LinkedHashMap<>();
        for (CartLine line : lines) {
            bySeller.computeIfAbsent(line.sellerId(), key -> new ArrayList<>()).add(line);
        }
        return bySeller;
    }

    // Tous les ateliers du panier doivent être onboardés : le reversement de l'un d'eux échouerait
    // sinon après encaissement, laissant des fonds bloqués.
    private void requireSellersPayable(Map<UUID, List<CartLine>> bySeller) {
        for (Map.Entry<UUID, List<CartLine>> entry : bySeller.entrySet()) {
            sellerAccountRepository.findByUser_Id(entry.getKey())
                    .filter(SellerAccount::isChargesEnabled)
                    .orElseThrow(() -> new BillingValidationException(
                            "L'atelier « " + entry.getValue().get(0).sellerName()
                                    + " » n'a pas encore activé les paiements — retire ses pièces du panier."));
        }
    }

    // Refus rapide (422, avant tout appel Stripe) si le stock ne couvre pas une ligne.
    private void requireStockAvailable(List<CartLine> lines) {
        for (CartLine line : lines) {
            if (line.product().getStock() < line.quantity()) {
                throw new BillingValidationException(
                        "Stock insuffisant pour « " + line.product().getName() + " » (reste "
                                + line.product().getStock() + ").");
            }
        }
    }

    // Réservation atomique du stock (anti-survente sous concurrence) : décrément conditionnel par
    // ligne. Un échec (course perdue) annule toute la transaction.
    private void reserveStock(List<CartLine> lines) {
        for (CartLine line : lines) {
            if (productRepository.decrementStock(line.product().getId(), line.quantity()) == 0) {
                throw new BillingValidationException(
                        "Stock insuffisant pour « " + line.product().getName() + " ».");
            }
        }
    }

    // Idempotence : un double-clic / retry réseau dans une même fenêtre courte réutilise le MÊME
    // PaymentIntent (au lieu d'en créer un nouveau + des commandes PENDING orphelines). Clé = acheteur
    // + signature du panier + bucket d'une minute (un ré-achat ultérieur du même panier obtient une
    // nouvelle clé, la clé Stripe expirant de toute façon sous 24 h).
    private String idempotencyKey(User buyer, List<CartLine> lines) {
        String cartSig = lines.stream()
                .map(l -> l.product().getId() + "x" + l.quantity())
                .sorted().collect(Collectors.joining(","));
        return "checkout:" + buyer.getId() + ":"
                + Integer.toHexString(cartSig.hashCode()) + ":" + (System.currentTimeMillis() / 60_000);
    }

    // Escrow : charge sur le compte PLATEFORME (ni on_behalf_of, ni transfer_data, ni application_fee).
    // Les fonds sont retenus jusqu'au reversement (Transfer) déclenché à la livraison.
    private PaymentIntent createIntent(User buyer, int amount, String currency,
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

    private List<PaymentIntentResponse.Shipment> shipments(Map<UUID, List<CartLine>> bySeller,
                                                           Map<UUID, Integer> shippingBySeller) {
        return bySeller.entrySet().stream()
                .map(e -> new PaymentIntentResponse.Shipment(
                        e.getValue().get(0).sellerName(),
                        e.getValue().stream().mapToInt(CartLine::quantity).sum(),
                        shippingBySeller.getOrDefault(e.getKey(), 0)))
                .toList();
    }

    private record CartLine(MarketplaceProduct product, int quantity) {

        int lineTotal() {
            return product.getPriceCents() * quantity;
        }

        UUID sellerId() {
            return product.getArtisanProfile().getUser().getId();
        }

        String sellerName() {
            return product.getArtisanProfile().getDisplayName();
        }
    }
}
