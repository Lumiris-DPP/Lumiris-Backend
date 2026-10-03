package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.WebhookSignatureException;
import com.minoh.lumiris_backend.service.BlockchainService;
import com.minoh.lumiris_backend.service.BuyerOrderService;
import com.minoh.lumiris_backend.service.OrderScheduler;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Paiement marketplace de bout en bout côté serveur, sur un vrai PostgreSQL (PostGIS, exigé par les
 * migrations) : réservation du stock, concurrence sur la dernière unité, répétitions, webhook et
 * balayage des paiements restés en attente. Stripe est remplacé par {@link FakeStripeApi}.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "stripe.secret-key=sk_test_dummy",
        "stripe.publishable-key=pk_test_dummy",
        "stripe.webhook-secret=" + MarketplaceCheckoutIntegrationTest.WEBHOOK_SECRET,
        "stripe.bootstrap-catalog=false",
        "stripe.products.solo=prod_dummy",
        "stripe.products.studio=prod_dummy",
        "stripe.products.maison=prod_dummy",
        "stripe.products.atelier-plus=prod_dummy",
        "stripe.products.local=prod_dummy",
        "blockchain.wallet.private-key=0x0000000000000000000000000000000000000000000000000000000000000001",
        "spring.cache.type=none",
})
class MarketplaceCheckoutIntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_integration_test";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    private static FakeStripeApi stripe;

    @Autowired
    private DirectSaleService directSaleService;

    @Autowired
    private StripeWebhookService webhookService;

    @Autowired
    private OrderScheduler orderScheduler;

    @Autowired
    private BuyerOrderService buyerOrderService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private BlockchainService blockchainService;

    @BeforeAll
    static void startStripe() throws Exception {
        stripe = new FakeStripeApi();
    }

    @AfterAll
    static void stopStripe() {
        stripe.close();
    }

    @BeforeEach
    void resetStripe() {
        stripe.reset();
    }

    // Deux acheteurs paient la dernière unité au même moment : un seul obtient la réservation, le
    // stock ne passe jamais sous zéro et une seule commande reste en attente.
    @Test
    void lastUnit_isReservedByExactlyOneOfTwoConcurrentBuyers() throws Exception {
        Variant variant = listing("EUR", 8900, 1);
        String first = buyer();
        String second = buyer();
        stripe.holdCreationsUntil(2);

        List<Outcome> outcomes = concurrently(
                () -> checkout(first, variant, 1),
                () -> checkout(second, variant, 1));

        assertThat(outcomes).filteredOn(o -> o.result() != null).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o.error() instanceof BillingValidationException).hasSize(1);
        assertThat(stock(variant)).isZero();
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // R01 : un double clic dans la même minute renvoie le même PaymentIntent, avec exactement les
    // mêmes paramètres sous la même clé d'idempotence.
    @Test
    void retryOfTheSameAttempt_sendsIdenticalParametersUnderTheSameKey() {
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();

        PaymentIntentResponse first = checkout(buyer, variant, 1);
        PaymentIntentResponse retry = checkout(buyer, variant, 1);

        assertThat(retry.clientSecret()).isEqualTo(first.clientSecret());
        List<FakeStripeApi.Request> creations = creations();
        assertThat(creations).hasSize(2);
        assertThat(creations.get(1).idempotencyKey()).isEqualTo(creations.get(0).idempotencyKey());
        assertThat(creations.get(1).params()).isEqualTo(creations.get(0).params());
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // Double clic : deux requêtes de la même tentative reçoivent le même PaymentIntent pendant que
    // la première n'a pas encore validé ; une seule réserve et crée les commandes.
    @Test
    void concurrentRetriesOfTheSameAttempt_reserveOnlyOnce() throws Exception {
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();
        stripe.holdCreationsUntil(2);

        List<Outcome> outcomes = concurrently(
                () -> checkout(buyer, variant, 1),
                () -> checkout(buyer, variant, 1));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(outcomes).extracting(o -> ((PaymentIntentResponse) o.result()).clientSecret()).containsOnly(
                ((PaymentIntentResponse) outcomes.get(0).result()).clientSecret());
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // R02 : le retry d'une tentative qui a déjà réservé la dernière unité réutilise sa réservation
    // au lieu d'être refusé pour un stock qu'elle détient elle-même.
    @Test
    void retryAfterReservingTheLastUnit_reusesTheReservation() {
        Variant variant = listing("EUR", 8900, 1);
        String buyer = buyer();

        PaymentIntentResponse first = checkout(buyer, variant, 1);
        PaymentIntentResponse retry = checkout(buyer, variant, 1);

        assertThat(retry.clientSecret()).isEqualTo(first.clientSecret());
        assertThat(stock(variant)).isZero();
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // Une nouvelle tentative (panier modifié) annule l'ancienne intention chez Stripe avant de remettre
    // sa réservation en rayon : l'ancien écran de paiement ne peut plus encaisser.
    @Test
    void newAttempt_cancelsTheSupersededIntentAtStripeAndMovesTheReservation() {
        Variant variant = listing("EUR", 8900, 2);
        String buyer = buyer();

        String firstIntent = intentId(checkout(buyer, variant, 1));
        String secondIntent = intentId(checkout(buyer, variant, 2));

        assertThat(secondIntent).isNotEqualTo(firstIntent);
        assertThat(stripe.status(firstIntent)).isEqualTo("canceled");
        assertThat(orderStatuses(firstIntent)).containsOnly("CANCELLED");
        assertThat(orderStatuses(secondIntent)).containsOnly("PENDING");
        assertThat(stock(variant)).isZero();
    }

    // R03 : un atelier encaissable mais dont l'abonnement n'est plus actif ne peut pas être payé,
    // même par une requête forgée qui contourne le catalogue.
    @Test
    void sellerWithoutActiveSubscription_isRefusedBeforeAnyStripeCall() {
        Variant variant = listing("EUR", 8900, 1);
        jdbc.update("update subscriptions set status = 'canceled' where user_id = ?", variant.sellerId());

        assertThatThrownBy(() -> checkout(buyer(), variant, 1)).isInstanceOf(BillingValidationException.class);
        assertThat(creations()).isEmpty();
        assertThat(stock(variant)).isEqualTo(1);
    }

    // R04 : un panier qui mélange deux devises n'est pas facturé dans la devise de sa première ligne.
    @Test
    void cartMixingCurrencies_isRefusedBeforeAnyStripeCall() {
        Variant euros = listing("EUR", 8900, 1);
        Variant dollars = listing("USD", 8900, 1);
        CartIntentRequest request = new CartIntentRequest(
                List.of(line(euros, 1), line(dollars, 1)), address());

        assertThatThrownBy(() -> directSaleService.createCartPaymentIntent(buyer(), request))
                .isInstanceOf(BillingValidationException.class);
        assertThat(creations()).isEmpty();
    }

    // R04 : un montant qui dépasse la capacité d'un entier est refusé au lieu de partir négatif chez Stripe.
    @Test
    void amountOverflowingAnInteger_isRefusedBeforeAnyStripeCall() {
        Variant variant = listing("EUR", 1_500_000_000, 2);

        assertThatThrownBy(() -> checkout(buyer(), variant, 2)).isInstanceOf(BillingValidationException.class);
        assertThat(creations()).isEmpty();
        assertThat(stock(variant)).isEqualTo(2);
    }

    // R05 : deux livraisons simultanées du même webhook ne produisent qu'une confirmation.
    @Test
    void simultaneousWebhookDeliveries_confirmTheOrderOnce() throws Exception {
        Variant variant = listing("EUR", 8900, 1);
        String intent = intentId(checkout(buyer(), variant, 1));
        String payload = paymentSucceeded(intent);

        List<Outcome> outcomes = concurrently(
                () -> deliver(payload),
                () -> deliver(payload));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(orderStatuses(intent)).containsOnly("PAID");
        assertThat(events(intent, "PAYMENT_CONFIRMED")).isEqualTo(1);
        assertThat(wardrobeItems(intent)).isEqualTo(1);
    }

    // Un webhook rejoué après coup ne produit aucun second effet.
    @Test
    void repeatedWebhook_hasNoSecondEffect() {
        Variant variant = listing("EUR", 8900, 1);
        String intent = intentId(checkout(buyer(), variant, 1));
        String payload = paymentSucceeded(intent);

        deliver(payload);
        deliver(payload);

        assertThat(orderStatuses(intent)).containsOnly("PAID");
        assertThat(events(intent, "PAYMENT_CONFIRMED")).isEqualTo(1);
        assertThat(wardrobeItems(intent)).isEqualTo(1);
    }

    // Une signature invalide est refusée et ne touche à aucune commande.
    @Test
    void webhookWithInvalidSignature_isRejectedWithoutEffect() {
        Variant variant = listing("EUR", 8900, 1);
        String intent = intentId(checkout(buyer(), variant, 1));
        String payload = paymentSucceeded(intent);

        assertThatThrownBy(() -> webhookService.handle(payload, signature(payload, "whsec_wrong")))
                .isInstanceOf(WebhookSignatureException.class);
        assertThat(orderStatuses(intent)).containsOnly("PENDING");
    }

    // Une confirmation pour un paiement inconnu n'invente rien : le webhook passe sans effet et
    // l'acheteur n'obtient pas de groupe de commande.
    @Test
    void unknownPaymentIntent_confirmsNothing() {
        String buyer = buyer();

        deliver(paymentSucceeded("pi_unknown_" + UUID.randomUUID()));

        assertThatThrownBy(() -> buyerOrderService.getMyOrderGroup(buyer, "pi_unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // R06 : un paiement réussi dont le webhook s'est perdu est confirmé par le balayage, pas annulé.
    @Test
    void stalePendingOrderWhosePaymentSucceeded_isConfirmedBySweep() {
        Variant variant = listing("EUR", 8900, 1);
        String intent = intentId(checkout(buyer(), variant, 1));
        stripe.setStatus(intent, "succeeded");
        makeStale(intent);

        orderScheduler.advanceStaleOrders();

        assertThat(orderStatuses(intent)).containsOnly("PAID");
        assertThat(events(intent, "PAYMENT_CONFIRMED")).isEqualTo(1);
        assertThat(stock(variant)).isZero();
        assertThat(stripe.count("POST", "/v1/payment_intents/" + intent + "/cancel")).isZero();
    }

    // R06 : un paiement abandonné est annulé chez Stripe avant que la pièce revienne au catalogue.
    @Test
    void stalePendingOrderNeverPaid_isCancelledAtStripeThenRestocked() {
        Variant variant = listing("EUR", 8900, 1);
        String intent = intentId(checkout(buyer(), variant, 1));
        makeStale(intent);

        orderScheduler.advanceStaleOrders();

        assertThat(stripe.status(intent)).isEqualTo("canceled");
        assertThat(orderStatuses(intent)).containsOnly("CANCELLED");
        assertThat(stock(variant)).isEqualTo(1);
    }

    // R06 : Stripe injoignable → la réservation est conservée ; le balayage suivant tranchera.
    @Test
    void stalePendingOrderWhileStripeIsUnreachable_keepsItsReservation() {
        Variant variant = listing("EUR", 8900, 1);
        String intent = intentId(checkout(buyer(), variant, 1));
        makeStale(intent);
        stripe.setUnavailable(true);

        orderScheduler.advanceStaleOrders();

        assertThat(orderStatuses(intent)).containsOnly("PENDING");
        assertThat(stock(variant)).isZero();
    }

    // ── Données et outils ───────────────────────────────────────────────────

    private record Variant(UUID id, UUID productId, UUID sellerId) {}

    private record Outcome(Object result, Throwable error) {}

    // Atelier abonné et encaissable, passeport valide, annonce publiée et une déclinaison en stock.
    private Variant listing(String currency, int priceCents, int stock) {
        UUID seller = user("ARTISAN");
        UUID profile = UUID.randomUUID();
        jdbc.update("insert into artisan_profiles (id, user_id, display_name, slug) values (?, ?, 'Atelier IT', ?)",
                profile, seller, "atelier-it-" + profile);
        jdbc.update("insert into subscriptions (user_id, plan_tier, billing_cycle, status) "
                + "values (?, 'ATELIER_SOLO', 'MONTHLY', 'active')", seller);
        jdbc.update("insert into seller_accounts (user_id, stripe_account_id, charges_enabled, payouts_enabled, "
                + "onboarding_completed) values (?, ?, true, true, true)", seller, "acct_it_" + profile);
        UUID dpp = UUID.randomUUID();
        jdbc.update("insert into dpp_forms (id, user_id, status, product_name) values (?, ?, 'VALID'::dpp_status, 'Veste IT')",
                dpp, seller);
        UUID product = UUID.randomUUID();
        jdbc.update("insert into marketplace_products (id, artisan_profile_id, dpp_form_id, name, price_cents, currency, "
                + "status, shipping_cents) values (?, ?, ?, 'Veste IT', ?, ?, 'PUBLISHED', 690)",
                product, profile, dpp, priceCents, currency);
        UUID variant = UUID.randomUUID();
        jdbc.update("insert into marketplace_product_variants (id, product_id, size_label, stock, position) "
                + "values (?, ?, 'M', ?, 0)", variant, product, stock);
        return new Variant(variant, product, seller);
    }

    private String buyer() {
        UUID id = user("CONSUMER");
        return jdbc.queryForObject("select email from users where id = ?", String.class, id);
    }

    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, role, name) values (?, ?, '{noop}x', ?, 'IT')",
                id, role.toLowerCase() + "-" + id + "@lumiris.test", role);
        return id;
    }

    private PaymentIntentResponse checkout(String buyerEmail, Variant variant, int quantity) {
        return directSaleService.createCartPaymentIntent(buyerEmail,
                new CartIntentRequest(List.of(line(variant, quantity)), address()));
    }

    private static CartIntentRequest.Line line(Variant variant, int quantity) {
        return new CartIntentRequest.Line(variant.productId(), variant.id(), quantity);
    }

    private static CartIntentRequest.ShippingAddress address() {
        return new CartIntentRequest.ShippingAddress("Acheteur IT", "1 rue du Test", null, "75001", "Paris", "FR", null);
    }

    private static String intentId(PaymentIntentResponse response) {
        return response.clientSecret().substring(0, response.clientSecret().indexOf("_secret_"));
    }

    private Object deliver(String payload) {
        webhookService.handle(payload, signature(payload, WEBHOOK_SECRET));
        return payload;
    }

    private static String paymentSucceeded(String intent) {
        return """
                {"id":"evt_%s","object":"event","api_version":"2024-06-20","type":"payment_intent.succeeded",
                 "data":{"object":{"id":"%s","object":"payment_intent","status":"succeeded",
                 "metadata":{"order_type":"marketplace"}}}}"""
                .formatted(UUID.randomUUID(), intent);
    }

    private static String signature(String payload, String secret) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Lance les opérations en même temps sur des threads distincts (une transaction chacune).
    private static List<Outcome> concurrently(Callable<?>... operations) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(operations.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> operation : operations) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return operation.call();
                }));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    outcomes.add(new Outcome(future.get(30, TimeUnit.SECONDS), null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome(null, e.getCause()));
                } catch (Exception e) {
                    outcomes.add(new Outcome(null, e));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<FakeStripeApi.Request> creations() {
        return stripe.requests().stream()
                .filter(r -> r.method().equals("POST") && r.path().equals("/v1/payment_intents"))
                .toList();
    }

    private void makeStale(String intent) {
        jdbc.update("update marketplace_orders set created_at = now() - interval '25 hours' "
                + "where stripe_payment_intent_id = ?", intent);
    }

    private int stock(Variant variant) {
        return jdbc.queryForObject("select stock from marketplace_product_variants where id = ?", Integer.class,
                variant.id());
    }

    private int pendingOrders(Variant variant) {
        return jdbc.queryForObject("select count(*) from marketplace_orders where variant_id = ? and status = 'PENDING'",
                Integer.class, variant.id());
    }

    private List<String> orderStatuses(String intent) {
        return jdbc.queryForList("select status from marketplace_orders where stripe_payment_intent_id = ?",
                String.class, intent);
    }

    private int events(String intent, String type) {
        return jdbc.queryForObject("select count(*) from marketplace_order_events e join marketplace_orders o "
                + "on o.id = e.order_id where o.stripe_payment_intent_id = ? and e.type = ?", Integer.class, intent, type);
    }

    private int wardrobeItems(String intent) {
        return jdbc.queryForObject("select count(*) from wardrobe_items w join marketplace_orders o "
                + "on o.id = w.order_id where o.stripe_payment_intent_id = ?", Integer.class, intent);
    }
}
