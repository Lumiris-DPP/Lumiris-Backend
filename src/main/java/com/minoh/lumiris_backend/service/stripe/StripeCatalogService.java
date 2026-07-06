package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.domain.BillingCycle;
import com.minoh.lumiris_backend.domain.PlanTier;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.PriceListParams;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Ensures one recurring price per cycle exists for each product, keyed by a stable lookup_key.
@Service
@RequiredArgsConstructor
public class StripeCatalogService {

    private static final Logger log = LoggerFactory.getLogger(StripeCatalogService.class);
    private static final String CURRENCY = "eur";

    private final StripeProperties properties;

    // lookup_key → price id
    private final ConcurrentHashMap<String, String> priceIdByLookupKey = new ConcurrentHashMap<>();
    // price id → {tier, cycle}
    private final ConcurrentHashMap<String, PlanCoordinate> coordinateByPriceId = new ConcurrentHashMap<>();

    public record PlanCoordinate(PlanTier tier, BillingCycle cycle, String priceId) {}

    public record CatalogEntry(
            PlanTier tier,
            String key,
            String displayName,
            String productId,
            String monthlyPriceId,
            String annualPriceId,
            long monthlyAmountCents,
            long annualAmountCents,
            boolean grantsPassports,
            Integer passportQuota,
            boolean unlimited
    ) {}

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (!properties.hasSecretKey()) {
            log.warn("Skipping Stripe catalogue bootstrap — no secret key configured.");
            return;
        }
        if (!properties.bootstrapCatalog()) {
            log.info("Stripe catalogue bootstrap disabled (stripe.bootstrap-catalog=false).");
            return;
        }
        log.info("Ensuring Stripe price catalogue for {} tiers …", PlanTier.values().length);
        int ensured = 0;
        for (PlanTier tier : PlanTier.values()) {
            for (BillingCycle cycle : BillingCycle.values()) {
                try {
                    ensurePrice(tier, cycle);
                    ensured++;
                } catch (StripeException e) {
                    log.error("Could not ensure price for {}/{} (product={}): {}",
                            tier.key(), cycle.key(), tier.productId(properties.products()), e.getMessage());
                }
            }
        }
        log.info("Stripe catalogue ready: {}/{} prices ensured.", ensured, PlanTier.values().length * 2);
    }

    public String priceId(PlanTier tier, BillingCycle cycle) {
        String cached = priceIdByLookupKey.get(tier.lookupKey(cycle));
        if (cached != null) {
            return cached;
        }
        return StripeCalls.billed("Unable to resolve Stripe price for " + tier.key() + "/" + cycle.key(),
                () -> ensurePrice(tier, cycle));
    }

    // Cache-only lookup (no network), so callers running inside a DB transaction never hit Stripe.
    // The cache is warmed by bootstrap()/ensurePrice(); a miss simply yields empty and the caller
    // can fall back to the already-loaded Stripe price object.
    public Optional<PlanCoordinate> cachedCoordinate(String priceId) {
        if (priceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(coordinateByPriceId.get(priceId));
    }

    // User-facing read: never calls Stripe. Prices are warmed by bootstrap() at startup;
    // a cold cache simply yields null price ids (amounts still come from the enum).
    public List<CatalogEntry> catalog() {
        List<CatalogEntry> entries = new ArrayList<>();
        for (PlanTier tier : PlanTier.values()) {
            entries.add(new CatalogEntry(
                    tier,
                    tier.key(),
                    tier.displayName(),
                    tier.productId(properties.products()),
                    cachedPriceId(tier, BillingCycle.MONTHLY),
                    cachedPriceId(tier, BillingCycle.ANNUAL),
                    tier.monthlyAmountCents(),
                    tier.annualAmountCents(),
                    tier.grantsPassports(),
                    tier.passportQuota(),
                    tier.isUnlimited()
            ));
        }
        return entries;
    }

    private String cachedPriceId(PlanTier tier, BillingCycle cycle) {
        return priceIdByLookupKey.get(tier.lookupKey(cycle));
    }

    private synchronized String ensurePrice(PlanTier tier, BillingCycle cycle) throws StripeException {
        String cachedKey = priceIdByLookupKey.get(tier.lookupKey(cycle));
        if (cachedKey != null) {
            return cachedKey;
        }
        String lookupKey = tier.lookupKey(cycle);

        PriceListParams listParams = PriceListParams.builder()
                .addLookupKey(lookupKey)
                .setActive(true)
                .setLimit(1L)
                .build();
        List<Price> existing = Price.list(listParams).getData();
        if (!existing.isEmpty()) {
            String id = existing.get(0).getId();
            cache(tier, cycle, id);
            return id;
        }

        PriceCreateParams.Recurring.Interval interval = cycle == BillingCycle.ANNUAL
                ? PriceCreateParams.Recurring.Interval.YEAR
                : PriceCreateParams.Recurring.Interval.MONTH;

        PriceCreateParams createParams = PriceCreateParams.builder()
                .setProduct(tier.productId(properties.products()))
                .setCurrency(CURRENCY)
                .setUnitAmount(tier.amountCents(cycle))
                .setLookupKey(lookupKey)
                .setTransferLookupKey(true)
                .setRecurring(PriceCreateParams.Recurring.builder().setInterval(interval).build())
                .build();

        Price created = Price.create(createParams);
        cache(tier, cycle, created.getId());
        log.info("Created Stripe price {} ({}/{}, {} c)", created.getId(), tier.key(), cycle.key(),
                tier.amountCents(cycle));
        return created.getId();
    }

    private void cache(PlanTier tier, BillingCycle cycle, String priceId) {
        priceIdByLookupKey.put(tier.lookupKey(cycle), priceId);
        coordinateByPriceId.put(priceId, new PlanCoordinate(tier, cycle, priceId));
    }
}
