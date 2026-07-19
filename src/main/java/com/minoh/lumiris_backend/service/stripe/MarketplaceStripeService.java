package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.repository.MarketplaceProductRepository;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketplaceStripeService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceStripeService.class);

    private final StripeProperties properties;
    private final MarketplaceProductRepository productRepository;

    @Transactional
    public void ensureStripeProduct(MarketplaceProduct product) {
        if (!properties.hasSecretKey()) {
            return;
        }
        if (product.getStripeProductId() != null && product.getStripePriceId() != null) {
            return; // déjà lié : on ne recrée jamais un produit Stripe pour la même annonce
        }
        String pid = product.getId().toString();
        try {
            String productId = product.getStripeProductId();
            if (productId == null) {
                Product sp = Product.create(
                        ProductCreateParams.builder()
                                .setName(product.getName())
                                .putMetadata("marketplace_product_id", pid)
                                .putMetadata("dpp_form_id",
                                        product.getDppForm() != null ? product.getDppForm().getId().toString() : "")
                                .build(),
                        RequestOptions.builder().setIdempotencyKey("mp-prod:" + pid).build());
                productId = sp.getId();
            }
            Price price = Price.create(
                    PriceCreateParams.builder()
                            .setProduct(productId)
                            .setCurrency(product.getCurrency().toLowerCase())
                            .setUnitAmount((long) product.getPriceCents())
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("mp-price:" + pid + ":" + product.getPriceCents()
                                    + ":" + product.getCurrency())
                            .build());
            product.setStripeProductId(productId);
            product.setStripePriceId(price.getId());
            productRepository.save(product);
            log.info("Stripe product/price ensured for marketplace product {} ({}/{})",
                    pid, productId, price.getId());
        } catch (com.stripe.exception.StripeException e) {
            log.warn("Could not ensure Stripe product for marketplace product {}: {}", pid, e.getMessage());
        }
    }

    // Session Checkout mode=payment pour ACHETER l'article in-app (paiement unique). Public (app VISION).
    public String createBuyCheckout(MarketplaceProduct product) {
        properties.requireSecretKey();
        if (product.getStripePriceId() == null) {
            throw new BillingValidationException("Cet article n'est pas en vente directe in-app.");
        }
        String base = properties.portalReturnUrl();
        return StripeCalls.billed("Ouverture du paiement impossible", () -> {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(product.getStripePriceId())
                            .setQuantity(1L)
                            .build())
                    .setSuccessUrl(base + (base.contains("?") ? "&" : "?") + "purchase=success")
                    .setCancelUrl(base + (base.contains("?") ? "&" : "?") + "purchase=cancel")
                    .putMetadata("marketplace_product_id", product.getId().toString())
                    .build();
            return Session.create(params).getUrl();
        });
    }
}
