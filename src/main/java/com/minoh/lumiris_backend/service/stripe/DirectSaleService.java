package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.out.OrderResponse;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.entity.*;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.*;
import com.stripe.model.PaymentIntent;
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

// LUMIRIS-22 · Achat direct in-app par PaymentIntent Stripe Connect (destination charge), confirmé via
// Payment Element EMBARQUÉ dans l'UI VISION (pas de redirection). Commission ~5% prélevée à la source
// (application_fee), reste transféré au compte connecté de l'artisan (payout net). Un seul vendeur/panier.
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

        // Un seul vendeur par panier (les destination charges ciblent un unique compte connecté).
        Set<UUID> sellerIds = lines.stream()
                .map(l -> l.product().getArtisanProfile().getUser().getId())
                .collect(Collectors.toSet());
        if (sellerIds.size() != 1) {
            throw new BillingValidationException(
                    "Un panier ne peut contenir que des pièces d'un même atelier — réglez chaque atelier séparément.");
        }
        User seller = lines.get(0).product().getArtisanProfile().getUser();
        SellerAccount sellerAccount = sellerAccountRepository.findByUser_Id(seller.getId())
                .filter(SellerAccount::isChargesEnabled)
                .orElseThrow(() -> new BillingValidationException(
                        "Le vendeur n'a pas encore activé les paiements (Stripe Connect)."));

        int itemsTotal = lines.stream().mapToInt(l -> l.product().getPriceCents() * l.quantity()).sum();
        int shipping = lines.stream().mapToInt(l -> l.product().getShippingCents()).max().orElse(0);
        int commission = (int) Math.round(itemsTotal * COMMISSION_RATE);
        int amount = itemsTotal + shipping;
        String currency = lines.get(0).product().getCurrency().toLowerCase();

        PaymentIntent intent = StripeCalls.billed("Préparation du paiement impossible", () ->
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                        .setAmount((long) amount)
                        .setCurrency(currency)
                        .setApplicationFeeAmount((long) commission)
                        .setOnBehalfOf(sellerAccount.getStripeAccountId())
                        .setTransferData(PaymentIntentCreateParams.TransferData.builder()
                                .setDestination(sellerAccount.getStripeAccountId())
                                .build())
                        .addPaymentMethodType("card")
                        .addPaymentMethodType("klarna")
                        .putMetadata("order_type", "marketplace")
                        .putMetadata("buyer_user_id", buyer.getId().toString())
                        .build()));

        // Une commande PENDING par ligne, rattachée au même PaymentIntent (fulfillment groupé au webhook).
        for (CartLine line : lines) {
            MarketplaceProduct p = line.product();
            int lineTotal = p.getPriceCents() * line.quantity();
            MarketplaceOrder order = new MarketplaceOrder();
            order.setProduct(p);
            order.setDppForm(p.getDppForm());
            order.setBuyer(buyer);
            order.setSeller(seller);
            order.setStripePaymentIntentId(intent.getId());
            order.setAmountTotalCents(lineTotal);
            order.setCommissionCents((int) Math.round(lineTotal * COMMISSION_RATE));
            order.setCurrency(p.getCurrency());
            order.setStatus(OrderStatus.PENDING);
            orderRepository.save(order);
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

    private record CartLine(MarketplaceProduct product, int quantity) {}
}
