package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.service.EmailOutboxDispatcher;

import com.minoh.lumiris_backend.marketplace.checkout.service.DirectSaleService;

import com.minoh.lumiris_backend.marketplace.checkout.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.ProductVariantForm;
import com.minoh.lumiris_backend.marketplace.catalog.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.marketplace.checkout.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.WebhookSignatureException;
import com.minoh.lumiris_backend.service.BlockchainService;
import com.minoh.lumiris_backend.marketplace.order.service.BuyerOrderService;
import com.minoh.lumiris_backend.marketplace.order.scheduling.OrderScheduler;
import com.minoh.lumiris_backend.marketplace.catalog.service.SellerCatalogService;
import io.minio.MinioClient;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Vérifie les paiements concurrents sur une base isolée.
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

        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '3s'",
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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private SellerCatalogService sellerCatalogService;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private EmailOutboxDispatcher emailOutboxDispatcher;

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private BlockchainService blockchainService;

    // Démarre le serveur local simulant les réponses Stripe.
    @BeforeAll
    static void startStripe() throws Exception {
        stripe = new FakeStripeApi();
    }

    // Ferme le serveur local simulant les réponses Stripe.
    @AfterAll
    static void stopStripe() {
        stripe.close();
    }

    // Réinitialise les paiements simulés avant chaque test.
    @BeforeEach
    void resetStripe() {
        stripe.reset();
    }

    // Vérifie qu'un seul acheteur réserve la dernière pièce.
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

    // Vérifie la stabilité des paramètres et de la clé rejouée.
    @Test
    void retryOfTheSameAttempt_sendsIdenticalParametersUnderTheSameKey() {
        awayFromMinuteEdge();
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

    // Vérifie qu'un double clic concurrent ne réserve qu'une fois.
    @Test
    void concurrentRetriesOfTheSameAttempt_reserveOnlyOnce() throws Exception {
        awayFromMinuteEdge();
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

    // Vérifie la réutilisation de la dernière pièce déjà réservée.
    @Test
    void retryAfterReservingTheLastUnit_reusesTheReservation() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 1);
        String buyer = buyer();

        PaymentIntentResponse first = checkout(buyer, variant, 1);
        PaymentIntentResponse retry = checkout(buyer, variant, 1);

        assertThat(retry.clientSecret()).isEqualTo(first.clientSecret());
        assertThat(stock(variant)).isZero();
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // Vérifie l'annulation du paiement remplacé avant sa nouvelle réservation.
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

    // Vérifie le nom du produit dans l'erreur sans déclinaison nommée.
    @Test
    void stockErrorKeepsProductNameWithoutVariantLabel() {
        Variant variant = listing("EUR", 8900, 0);
        jdbc.update("update marketplace_product_variants set size_label = null, color_label = ' ' where id = ?",
                variant.id());
        String name = jdbc.queryForObject("select name from marketplace_products where id = ?",
                String.class, variant.productId());

        assertThatThrownBy(() -> checkout(buyer(), variant, 1))
                .isInstanceOf(BillingValidationException.class)
                .hasMessage("Stock insuffisant pour « " + name + " » (reste 0).");
        assertThat(creations()).isEmpty();
        assertThat(pendingOrders(variant)).isZero();
    }

    // Vérifie les tailles et couleurs dans les erreurs de stock.
    @ParameterizedTest
    @CsvSource({"M, bleu, M · bleu", "M, '', M", "'', bleu, bleu"})
    void stockErrorKeepsSizeAndColorLabels(String size, String color, String expectedLabel) {
        Variant variant = listing("EUR", 8900, 0);
        jdbc.update("update marketplace_product_variants set size_label = ?, color_label = ? where id = ?",
                size, color, variant.id());
        String name = jdbc.queryForObject("select name from marketplace_products where id = ?",
                String.class, variant.productId());

        assertThatThrownBy(() -> checkout(buyer(), variant, 1))
                .isInstanceOf(BillingValidationException.class)
                .hasMessage("Stock insuffisant pour « " + name + " (" + expectedLabel + ") » (reste 0).");
        assertThat(creations()).isEmpty();
        assertThat(pendingOrders(variant)).isZero();
    }

    // Vérifie le refus d'un atelier sans abonnement avant Stripe.
    @Test
    void sellerWithoutActiveSubscription_isRefusedBeforeAnyStripeCall() {
        Variant variant = listing("EUR", 8900, 1);
        jdbc.update("update subscriptions set status = 'canceled' where user_id = ?", variant.sellerId());

        assertThatThrownBy(() -> checkout(buyer(), variant, 1)).isInstanceOf(BillingValidationException.class);
        assertThat(creations()).isEmpty();
        assertThat(stock(variant)).isEqualTo(1);
    }

    // Vérifie le refus de devises différentes avant Stripe.
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

    // Vérifie le refus d'un dépassement de montant avant Stripe.
    @Test
    void amountOverflowingAnInteger_isRefusedBeforeAnyStripeCall() {
        Variant variant = listing("EUR", 1_500_000_000, 2);

        assertThatThrownBy(() -> checkout(buyer(), variant, 2)).isInstanceOf(BillingValidationException.class);
        assertThat(creations()).isEmpty();
        assertThat(stock(variant)).isEqualTo(2);
    }

    // Vérifie que les notifications simultanées confirment la commande une fois.
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

    // Vérifie qu'une notification rejouée ne répète aucun effet.
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

    // Vérifie le rejet sans effet d'une signature Stripe invalide.
    @Test
    void webhookWithInvalidSignature_isRejectedWithoutEffect() {
        Variant variant = listing("EUR", 8900, 1);
        String intent = intentId(checkout(buyer(), variant, 1));
        String payload = paymentSucceeded(intent);

        assertThatThrownBy(() -> webhookService.handle(payload, signature(payload, "whsec_wrong")))
                .isInstanceOf(WebhookSignatureException.class);
        assertThat(orderStatuses(intent)).containsOnly("PENDING");
    }

    // Vérifie qu'un paiement inconnu ne confirme aucune commande.
    @Test
    void unknownPaymentIntent_confirmsNothing() {
        String buyer = buyer();

        deliver(paymentSucceeded("pi_unknown_" + UUID.randomUUID()));

        assertThatThrownBy(() -> buyerOrderService.getMyOrderGroup(buyer, "pi_unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Vérifie la confirmation d'un ancien paiement encaissé sans notification.
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

    // Vérifie l'annulation du paiement impayé avant la remise en stock.
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

    // Vérifie qu'une panne Stripe conserve la réservation existante.
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

    // Vérifie que les créations concurrentes partagent la même réservation.
    @Test
    void concurrentRetriesWhileStripeIsStillProcessing_reserveOnlyOnce() throws Exception {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();
        stripe.slowCreations(600);

        List<Outcome> outcomes = concurrently(
                () -> checkout(buyer, variant, 1),
                () -> checkout(buyer, variant, 1));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(creations()).hasSizeGreaterThan(2);
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // Vérifie que la confirmation concurrente conserve l'état payé et la facture.
    @Test
    void retryWhileTheWebhookConfirms_keepsThePaidStatusAndInvoice() throws Exception {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();
        String intent = intentId(checkout(buyer, variant, 1));
        CountDownLatch rowsLocked = new CountDownLatch(1);

        List<Outcome> outcomes = concurrently(
                () -> new TransactionTemplate(transactionManager).execute(status -> {

                    jdbc.queryForList("select id from marketplace_orders where stripe_payment_intent_id = ? for no key update",
                            intent);
                    rowsLocked.countDown();
                    pause(1500);
                    directSaleService.fulfillByPaymentIntent(intent);
                    return intent;
                }),
                () -> {
                    rowsLocked.await(10, TimeUnit.SECONDS);
                    return checkout(buyer, variant, 1, address("2 rue du Retry"));
                });

        assertThat(outcomes.get(0).error()).isNull();
        assertThat(outcomes.get(1).error()).isInstanceOf(BillingValidationException.class);
        assertThat(orderStatuses(intent)).containsOnly("PAID");
        assertThat(invoiceNumbers(intent)).doesNotContainNull();
        assertThat(events(intent, "PAYMENT_CONFIRMED")).isEqualTo(1);
    }

    // Vérifie le refus d'un retry après la commande payée.
    @Test
    void retryAfterPayment_isRefusedWithoutTouchingThePaidOrder() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();
        String intent = intentId(checkout(buyer, variant, 1));
        deliver(paymentSucceeded(intent));

        assertThatThrownBy(() -> checkout(buyer, variant, 1, address("2 rue du Retry")))
                .isInstanceOf(BillingValidationException.class);
        assertThat(orderStatuses(intent)).containsOnly("PAID");
        assertThat(shippingLines(intent)).containsOnly("1 rue du Test");
    }

    // Vérifie qu'un ancien paiement annulé ouvre une nouvelle tentative.
    @Test
    void returnToACancelledAttemptWithinTheMinute_startsAFreshIntent() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();

        String first = intentId(checkout(buyer, variant, 1));
        String second = intentId(checkout(buyer, variant, 2));
        String back = intentId(checkout(buyer, variant, 1));

        assertThat(stripe.status(first)).isEqualTo("canceled");
        assertThat(stripe.status(second)).isEqualTo("canceled");
        assertThat(back).isNotIn(first, second);
        assertThat(orderStatuses(first)).containsOnly("CANCELLED");
        assertThat(orderStatuses(back)).containsOnly("PENDING");
        assertThat(stock(variant)).isEqualTo(2);
    }

    // Vérifie que deux retries croisés rendent des paiements encore utilisables.
    @Test
    void crossedRetries_settleBothOldAttemptsAndReturnLiveIntentsWithoutLockTimeout() throws Exception {
        int secondOfMinute = LocalTime.now().getSecond();
        if (secondOfMinute >= 40) {
            pause((61 - secondOfMinute) * 1000L);
        }
        Variant first = listing("EUR", 8900, 2);
        Variant second = listing("EUR", 8900, 2);
        String buyer = buyer();
        String x = intentId(checkout(buyer, first, 1));
        stripe.setStatus(x, "processing");
        String y = intentId(checkout(buyer, second, 1));
        assertThat(orderStatuses(x)).containsExactly("PENDING");
        assertThat(orderStatuses(y)).containsExactly("PENDING");
        stripe.setStatus(x, "requires_payment_method");
        stripe.setStatus(y, "requires_payment_method");
        stripe.holdCreationsUntil(2);
        stripe.holdCancellationsUntil(2);

        long started = System.nanoTime();
        List<Outcome> outcomes = concurrently(
                () -> checkout(buyer, first, 1, address("2 rue du Retry X")),
                () -> checkout(buyer, second, 1, address("3 rue du Retry Y")));

        assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)).isLessThan(15);
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        String freshX = intentId((PaymentIntentResponse) outcomes.get(0).result());
        String freshY = intentId((PaymentIntentResponse) outcomes.get(1).result());
        assertThat(freshX).isNotIn(x, y, freshY);
        assertThat(freshY).isNotIn(x, y);
        for (String old : List.of(x, y)) {
            assertThat(stripe.status(old)).isEqualTo("canceled");
            assertThat(orderStatuses(old)).containsExactly("CANCELLED");
            assertThat(shippingLines(old)).containsExactly("1 rue du Test");
            assertThat(events(old, "CANCELLED")).isEqualTo(1);
        }
        for (String fresh : List.of(freshX, freshY)) {
            assertThat(stripe.status(fresh)).isEqualTo("requires_payment_method");
            assertThat(orderStatuses(fresh)).containsExactly("PENDING");
            assertThat(events(fresh, "PAYMENT_CONFIRMED")).isZero();
            assertThat(wardrobeItems(fresh)).isZero();
        }
        assertThat(shippingLines(freshX)).containsExactly("2 rue du Retry X");
        assertThat(shippingLines(freshY)).containsExactly("3 rue du Retry Y");
        assertThat(stock(first)).isEqualTo(1);
        assertThat(stock(second)).isEqualTo(1);
        assertThat(pendingOrders(first)).isEqualTo(1);
        assertThat(pendingOrders(second)).isEqualTo(1);
    }

    // Vérifie le refus du checkout dans une transaction existante.
    @Test
    void checkoutInsideAnExistingTransaction_isRefusedBeforeStripeOrStockChanges() {
        Variant variant = listing("EUR", 8900, 1);
        String buyer = buyer();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> checkout(buyer, variant, 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(creations()).isEmpty();
        assertThat(stock(variant)).isEqualTo(1);
        assertThat(pendingOrders(variant)).isZero();
    }

    // Vérifie la relecture après confirmation entre les phases du checkout.
    @Test
    void webhookBetweenCheckoutPhases_withOpenEntityManager_preservesPaidOrderAndAddress() {
        awayFromMinuteEdge();
        Variant first = listing("EUR", 8900, 2);
        Variant second = listing("EUR", 8900, 2);
        String buyer = buyer();
        String x = intentId(checkout(buyer, first, 1));
        stripe.setStatus(x, "processing");
        String y = intentId(checkout(buyer, second, 1));
        stripe.setStatus(x, "requires_payment_method");
        AtomicReference<List<Map<String, Object>>> paidRows = new AtomicReference<>();
        stripe.onNextCancellation(() -> {
            deliver(paymentSucceeded(x));
            paidRows.set(jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", x));
        });

        var entityManager = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
        try {
            assertThatThrownBy(() -> checkout(buyer, first, 1, address("2 rue du Retry")))
                    .isInstanceOf(BillingValidationException.class);
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            entityManager.close();
        }
        assertThat(paidRows.get()).isNotNull();
        assertThat(jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", x))
                .isEqualTo(paidRows.get());
        assertThat(orderStatuses(x)).containsExactly("PAID");
        assertThat(shippingLines(x)).containsExactly("1 rue du Test");
        assertThat(invoiceNumbers(x)).doesNotContainNull();
        assertThat(events(x, "PAYMENT_CONFIRMED")).isEqualTo(1);
        assertThat(wardrobeItems(x)).isEqualTo(1);
        assertThat(orderStatuses(y)).containsExactly("CANCELLED");
        assertThat(stock(first)).isEqualTo(1);
        assertThat(stock(second)).isEqualTo(2);
    }

    // Vérifie les clés bornées et stables après plusieurs annulations.
    @Test
    void repeatedlyCancelledAttempt_keepsBoundedStableKeysAndOneReservation() {

        int second = LocalTime.now().getSecond();
        if (second >= 30) {
            pause((61 - second) * 1000L);
        }
        Variant variant = listing("EUR", 8900, 1);
        String buyer = buyer();
        PaymentIntentResponse current = checkout(buyer, variant, 1);
        String previousKey = creations().getLast().idempotencyKey();
        List<String> cancelled = new ArrayList<>();
        List<String> derivedKeys = new ArrayList<>();
        List<String> expectedKeys = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String previousIntent = intentId(current);
            cancelled.add(previousIntent);
            stripe.setStatus(previousIntent, "canceled");
            directSaleService.settlePendingPayment(previousIntent);
            current = checkout(buyer, variant, 1);
            String expectedKey = "checkout:after:" + UUID.nameUUIDFromBytes(
                    (previousKey + ":after:" + previousIntent).getBytes(StandardCharsets.UTF_8));
            FakeStripeApi.Request last = creations().getLast();
            derivedKeys.add(last.idempotencyKey());
            expectedKeys.add(expectedKey);
            assertThat(checkout(buyer, variant, 1).clientSecret()).isEqualTo(current.clientSecret());
            assertThat(creations().getLast().idempotencyKey()).isEqualTo(last.idempotencyKey());
            assertThat(creations().getLast().params()).isEqualTo(last.params());
            previousKey = last.idempotencyKey();
        }
        assertThat(derivedKeys).containsExactlyElementsOf(expectedKeys).allSatisfy(key -> assertThat(key).hasSize(51));
        assertThat(creations()).allSatisfy(r -> assertThat(r.idempotencyKey().length()).isLessThanOrEqualTo(255));
        cancelled.forEach(id -> assertThat(orderStatuses(id)).containsExactly("CANCELLED"));
        assertThat(orderStatuses(intentId(current))).containsExactly("PENDING");
        assertThat(pendingOrders(variant)).isEqualTo(1);
        assertThat(stock(variant)).isZero();
    }

    // Vérifie le refus du retry avant une notification de paiement retardée.
    @Test
    void retryBeforeThePaidWebhook_isRefusedAndPreservesTheOriginalOrder() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();
        String intent = intentId(checkout(buyer, variant, 1));
        var before = jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", intent);
        stripe.setStatus(intent, "succeeded");

        assertThatThrownBy(() -> checkout(buyer, variant, 1, address("2 rue du Retry")))
                .isInstanceOf(BillingValidationException.class).hasMessageContaining("vient d'être payé");
        assertThat(jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", intent))
                .isEqualTo(before);
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(events(intent, "PAYMENT_CONFIRMED")).isZero();
        assertThat(wardrobeItems(intent)).isZero();
        assertThat(creations()).hasSize(2);
        assertThat(stripe.count("POST", "/v1/payment_intents/" + intent + "/cancel")).isZero();

        deliver(paymentSucceeded(intent));
        assertThat(orderStatuses(intent)).containsExactly("PAID");
        assertThat(shippingLines(intent)).containsExactly("1 rue du Test");
        assertThat(invoiceNumbers(intent)).doesNotContainNull();
        assertThat(events(intent, "PAYMENT_CONFIRMED")).isEqualTo(1);
        assertThat(wardrobeItems(intent)).isEqualTo(1);
        assertThat(stock(variant)).isEqualTo(2);
    }

    // Vérifie les adresses et réservations pendant un paiement en cours.
    @Test
    void retryWhilePaymentIsSettling_preservesAddressStockAndPendingOrder() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 1);
        String buyer = buyer();
        String intent = intentId(checkout(buyer, variant, 1));
        var before = jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", intent);
        for (String status : List.of("processing", "requires_capture")) {
            stripe.setStatus(intent, status);
            assertThatThrownBy(() -> checkout(buyer, variant, 1, address("2 rue du Retry")))
                    .isInstanceOf(BillingValidationException.class).hasMessageContaining("en cours");
            assertThat(jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", intent))
                    .isEqualTo(before);
            assertThat(stock(variant)).isZero();
            assertThat(events(intent, "PAYMENT_CONFIRMED")).isZero();
            assertThat(wardrobeItems(intent)).isZero();
        }
        assertThat(pendingOrders(variant)).isEqualTo(1);
        assertThat(stripe.count("POST", "/v1/payment_intents/" + intent + "/cancel")).isZero();
    }

    // Vérifie la libération des seules réservations du paiement annulé.
    @Test
    void retryOfStripeCancelledPendingAttempt_releasesOnlyPendingAndStartsFresh() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 1);
        String buyer = buyer();
        String cancelled = intentId(checkout(buyer, variant, 1));
        stripe.setStatus(cancelled, "canceled");

        String fresh = intentId(checkout(buyer, variant, 1, address("2 rue du Retry")));

        assertThat(fresh).isNotEqualTo(cancelled);
        assertThat(orderStatuses(cancelled)).containsExactly("CANCELLED");
        assertThat(shippingLines(cancelled)).containsExactly("1 rue du Test");
        assertThat(orderStatuses(fresh)).containsExactly("PENDING");
        assertThat(shippingLines(fresh)).containsExactly("2 rue du Retry");
        assertThat(events(cancelled, "CANCELLED")).isEqualTo(1);
        assertThat(stock(variant)).isZero();
        assertThat(pendingOrders(variant)).isEqualTo(1);
        assertThat(checkout(buyer, variant, 1).clientSecret()).startsWith(fresh + "_secret_");
        assertThat(events(cancelled, "CANCELLED")).isEqualTo(1);
    }

    // Vérifie qu'un paiement annulé ne rouvre aucune commande payée.
    @Test
    void retryOfStripeCancelledPaidAttempt_doesNotReopenThePaidOrder() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();
        String intent = intentId(checkout(buyer, variant, 1));
        deliver(paymentSucceeded(intent));
        var before = jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", intent);
        stripe.setStatus(intent, "canceled");

        assertThatThrownBy(() -> checkout(buyer, variant, 1, address("2 rue du Retry")))
                .isInstanceOf(BillingValidationException.class);
        assertThat(jdbc.queryForList("select * from marketplace_orders where stripe_payment_intent_id = ?", intent))
                .isEqualTo(before);
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(events(intent, "PAYMENT_CONFIRMED")).isEqualTo(1);
        assertThat(events(intent, "CANCELLED")).isZero();
        assertThat(wardrobeItems(intent)).isEqualTo(1);
        assertThat(creations()).hasSize(2);
    }

    // Vérifie que l'annulation survit à l'échec de la nouvelle réservation.
    @Test
    void failedReservationAfterCancellingTheSupersededIntent_keepsTheCancellation() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 2);
        String buyer = buyer();
        String first = intentId(checkout(buyer, variant, 1));

        CartIntentRequest request = new CartIntentRequest(List.of(line(variant, 2), line(variant, 1)), address());

        assertThatThrownBy(() -> directSaleService.createCartPaymentIntent(buyer, request))
                .isInstanceOf(BillingValidationException.class);
        assertThat(stripe.status(first)).isEqualTo("canceled");
        assertThat(orderStatuses(first)).containsOnly("CANCELLED");
        assertThat(stock(variant)).isEqualTo(2);
    }

    // Vérifie qu'un paiement en échec ne bloque pas les suivants.
    @Test
    void sweepWithOneFailingPayment_stillSettlesTheOthers() {
        Variant healthy = listing("EUR", 8900, 1);
        Variant broken = listing("EUR", 8900, 1);
        String healthyIntent = intentId(checkout(buyer(), healthy, 1));
        String brokenIntent = intentId(checkout(buyer(), broken, 1));
        makeStale(healthyIntent);
        makeStale(brokenIntent);

        jdbc.update("update marketplace_product_variants set stock = 2147483647 where id = ?", broken.id());
        try {
            orderScheduler.advanceStaleOrders();

            assertThat(orderStatuses(healthyIntent)).containsOnly("CANCELLED");
            assertThat(stock(healthy)).isEqualTo(1);
            assertThat(orderStatuses(brokenIntent)).containsOnly("PENDING");
        } finally {
            jdbc.update("update marketplace_orders set status = 'CANCELLED' where stripe_payment_intent_id = ?",
                    brokenIntent);
        }
    }

    // Vérifie la traduction du montant trop élevé en erreur de validation.
    @Test
    void amountAboveTheStripeMaximum_isRefusedAsInvalid() {
        Variant variant = listing("EUR", 60_000_000, 2);

        assertThatThrownBy(() -> checkout(buyer(), variant, 2)).isInstanceOf(BillingValidationException.class);
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(pendingOrders(variant)).isZero();
    }

    // Vérifie qu'une modification artisan n'écrase pas la réservation concurrente.
    @Test
    void saleDuringAnArtisanSave_isNotOverwrittenByTheSave() throws Exception {
        Variant variant = listing("EUR", 8900, 1);

        UpdateProductRequest edit = edit(variant, List.of(
                new ProductVariantForm(variant.id(), "M", null, null, "SKU-NEW", 1, 0, 0L)));

        List<Outcome> outcomes = duringArtisanSave(variant, edit, () -> checkout(buyer(), variant, 1));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(stock(variant)).isZero();
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // Vérifie la réservation de deux déclinaisons pendant une modification artisan.
    @Test
    void cartWithTwoVariantsDuringAnArtisanSave_reservesBothWithoutDeadlock() throws Exception {
        Variant medium = listing("EUR", 8900, 2);
        UUID largeId = UUID.randomUUID();
        jdbc.update("insert into marketplace_product_variants (id, product_id, size_label, stock, position) "
                + "values (?, ?, 'L', 2, 1)", largeId, medium.productId());
        Variant large = new Variant(largeId, medium.productId(), medium.sellerId());
        UpdateProductRequest edit = edit(medium, List.of(
                new ProductVariantForm(medium.id(), "M", null, null, "SKU-M", 2, 0, 0L),
                new ProductVariantForm(large.id(), "L", null, null, "SKU-L", 2, 1, 0L)));

        List<CartIntentRequest.Line> lines = new ArrayList<>(List.of(line(medium, 1), line(large, 1)));
        lines.sort((a, b) -> b.variantId().toString().compareTo(a.variantId().toString()));
        String buyer = buyer();

        List<Outcome> outcomes = duringArtisanSave(medium, edit,
                () -> directSaleService.createCartPaymentIntent(buyer, new CartIntentRequest(lines, address())));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(stock(medium)).isEqualTo(1);
        assertThat(stock(large)).isEqualTo(1);
    }

    // Regroupe les références d'une déclinaison créée pour le test.
    private record Variant(UUID id, UUID productId, UUID sellerId) {}

    // Conserve le résultat ou l'erreur d'une opération concurrente.
    private record Outcome(Object result, Throwable error) {}

    // Crée une pièce vendable et sa déclinaison dans la base isolée.
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

    // Crée un acheteur distinct pour le test.
    private String buyer() {
        UUID id = user("CONSUMER");
        return jdbc.queryForObject("select email from users where id = ?", String.class, id);
    }

    // Crée un utilisateur de test avec le rôle demandé.
    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, role, name) values (?, ?, '{noop}x', ?, 'IT')",
                id, role.toLowerCase() + "-" + id + "@lumiris.test", role);
        return id;
    }

    // Prépare une modification artisan de la pièce testée.
    private UpdateProductRequest edit(Variant variant, List<ProductVariantForm> variants) {
        UUID dpp = jdbc.queryForObject("select dpp_form_id from marketplace_products where id = ?", UUID.class,
                variant.productId());
        return new UpdateProductRequest("Veste IT", null, null, "Lin", null, 8900, "EUR", 690, null, 0, 0,
                variants, List.of(), null, null, dpp, MarketplaceProductStatus.PUBLISHED);
    }

    // Croise une modification artisan et l'opération testée avec des bornes.
    private List<Outcome> duringArtisanSave(Variant variant, UpdateProductRequest edit, Callable<?> concurrent)
            throws Exception {
        String artisan = jdbc.queryForObject("select email from users where id = ?", String.class, variant.sellerId());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement(
                    "select 1 from marketplace_products where id = ? for no key update")) {
                lock.setObject(1, variant.productId());
                lock.executeQuery();
            }
            Future<?> save = pool.submit(() -> sellerCatalogService.update(artisan, variant.productId(), edit));
            awaitLockWaiters(1);
            Future<?> other = pool.submit(concurrent);
            pause(1500);
            blocker.commit();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<?> future : List.of(save, other)) {
                try {
                    outcomes.add(new Outcome(future.get(30, TimeUnit.SECONDS), null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome(null, e.getCause()));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    // Attend un nombre fixé de transactions bloquées, sans attente infinie.
    private void awaitLockWaiters(int count) {
        for (int i = 0; i < 200; i++) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity "
                    + "where wait_event_type = 'Lock' and datname = current_database()", Integer.class);
            if (waiting != null && waiting >= count) {
                return;
            }
            pause(50);
        }
        throw new IllegalStateException("Aucune transaction en attente de verrou");
    }

    // Demande le paiement de la déclinaison avec l'adresse testée.
    private PaymentIntentResponse checkout(String buyerEmail, Variant variant, int quantity) {
        return checkout(buyerEmail, variant, quantity, address());
    }

    // Compose une ligne de panier pour la déclinaison demandée.
    private static CartIntentRequest.Line line(Variant variant, int quantity) {
        return new CartIntentRequest.Line(variant.productId(), variant.id(), quantity);
    }

    // Demande le paiement de la déclinaison avec l'adresse testée.
    private PaymentIntentResponse checkout(String buyerEmail, Variant variant, int quantity,
                                           CartIntentRequest.ShippingAddress shipping) {
        return directSaleService.createCartPaymentIntent(buyerEmail,
                new CartIntentRequest(List.of(line(variant, quantity)), shipping));
    }

    // Compose une adresse de livraison pour le test.
    private static CartIntentRequest.ShippingAddress address() {
        return address("1 rue du Test");
    }

    // Compose une adresse de livraison pour le test.
    private static CartIntentRequest.ShippingAddress address(String line1) {
        return new CartIntentRequest.ShippingAddress("Acheteur IT", line1, null, "75001", "Paris", "FR", null);
    }

    // Évite un changement de minute pendant le test d'idempotence.
    private static void awayFromMinuteEdge() {
        int second = LocalTime.now().getSecond();
        if (second >= 55) {
            pause((61 - second) * 1000L);
        }
    }

    // Attend la durée demandée en conservant les interruptions.
    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // Extrait la référence du paiement depuis la réponse.
    private static String intentId(PaymentIntentResponse response) {
        return response.clientSecret().substring(0, response.clientSecret().indexOf("_secret_"));
    }

    // Transmet un événement au traitement des notifications Stripe.
    private Object deliver(String payload) {
        webhookService.handle(payload, signature(payload, WEBHOOK_SECRET));
        return payload;
    }

    // Compose une notification de paiement réussi pour le test.
    private static String paymentSucceeded(String intent) {
        return """
                {"id":"evt_%s","object":"event","api_version":"2024-06-20","type":"payment_intent.succeeded",
                 "data":{"object":{"id":"%s","object":"payment_intent","status":"succeeded",
                 "metadata":{"order_type":"marketplace"}}}}"""
                .formatted(UUID.randomUUID(), intent);
    }

    // Signe le contenu simulé avec la clé de test.
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

    // Exécute les opérations ensemble avec des attentes bornées.
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

    // Liste les appels de création de paiement reçus.
    private List<FakeStripeApi.Request> creations() {
        return stripe.requests().stream()
                .filter(r -> r.method().equals("POST") && r.path().equals("/v1/payment_intents"))
                .toList();
    }

    // Vieillit une tentative pour déclencher le traitement périodique.
    private void makeStale(String intent) {
        jdbc.update("update marketplace_orders set created_at = now() - interval '25 hours' "
                + "where stripe_payment_intent_id = ?", intent);
    }

    // Lit le stock de la déclinaison dans la base isolée.
    private int stock(Variant variant) {
        return jdbc.queryForObject("select stock from marketplace_product_variants where id = ?", Integer.class,
                variant.id());
    }

    // Compte les commandes en attente pour la déclinaison testée.
    private int pendingOrders(Variant variant) {
        return jdbc.queryForObject("select count(*) from marketplace_orders where variant_id = ? and status = 'PENDING'",
                Integer.class, variant.id());
    }

    // Lit les états des commandes rattachées au paiement testé.
    private List<String> orderStatuses(String intent) {
        return jdbc.queryForList("select status from marketplace_orders where stripe_payment_intent_id = ?",
                String.class, intent);
    }

    // Compte les événements du type demandé pour la commande.
    private int events(String intent, String type) {
        return jdbc.queryForObject("select count(*) from marketplace_order_events e join marketplace_orders o "
                + "on o.id = e.order_id where o.stripe_payment_intent_id = ? and e.type = ?", Integer.class, intent, type);
    }

    // Lit les adresses des commandes rattachées au paiement testé.
    private List<String> shippingLines(String intent) {
        return jdbc.queryForList("select ship_to_line1 from marketplace_orders where stripe_payment_intent_id = ?",
                String.class, intent);
    }

    // Lit les factures des commandes rattachées au paiement testé.
    private List<String> invoiceNumbers(String intent) {
        return jdbc.queryForList("select invoice_number from marketplace_orders where stripe_payment_intent_id = ?",
                String.class, intent);
    }

    // Compte les pièces acquises depuis le paiement testé.
    private int wardrobeItems(String intent) {
        return jdbc.queryForObject("select count(*) from wardrobe_items w join marketplace_orders o "
                + "on o.id = w.order_id where o.stripe_payment_intent_id = ?", Integer.class, intent);
    }
}
