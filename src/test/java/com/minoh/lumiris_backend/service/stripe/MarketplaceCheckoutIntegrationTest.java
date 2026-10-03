package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.dto.in.CartIntentRequest;
import com.minoh.lumiris_backend.dto.in.ProductVariantForm;
import com.minoh.lumiris_backend.dto.in.UpdateProductRequest;
import com.minoh.lumiris_backend.dto.out.PaymentIntentResponse;
import com.minoh.lumiris_backend.entity.MarketplaceProductStatus;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.WebhookSignatureException;
import com.minoh.lumiris_backend.service.BlockchainService;
import com.minoh.lumiris_backend.service.BuyerOrderService;
import com.minoh.lumiris_backend.service.OrderScheduler;
import com.minoh.lumiris_backend.service.SellerCatalogService;
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
import org.springframework.transaction.PlatformTransactionManager;
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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SellerCatalogService sellerCatalogService;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private BlockchainService blockchainService;

    // Démarre le faux Stripe partagé par les scénarios de cette suite.
    @BeforeAll
    static void startStripe() throws Exception {
        stripe = new FakeStripeApi();
    }

    // Ferme le faux Stripe après la suite.
    @AfterAll
    static void stopStripe() {
        stripe.close();
    }

    // Réinitialise le faux Stripe avant chaque scénario.
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

    // Double clic, Stripe ayant déjà répondu : deux requêtes de la même tentative reçoivent le même
    // PaymentIntent pendant que la première n'a pas encore validé ; une seule réserve.
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

    // R02 : le retry d'une tentative qui a déjà réservé la dernière unité réutilise sa réservation
    // au lieu d'être refusé pour un stock qu'elle détient elle-même.
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

    // Double clic, Stripe traitant encore la première requête : la seconde reçoit un conflit 409, le
    // SDK la rejoue, et une seule réservation est faite.
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

    // F2 : un retry de la même tentative pendant que le webhook confirme le paiement ne défait pas
    // la confirmation : statut PAID et facture conservés, le retry est refusé.
    @Test
    void retryWhileTheWebhookConfirms_keepsThePaidStatusAndInvoice() throws Exception {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 3);
        String buyer = buyer();
        String intent = intentId(checkout(buyer, variant, 1));
        CountDownLatch rowsLocked = new CountDownLatch(1);

        List<Outcome> outcomes = concurrently(
                () -> new TransactionTemplate(transactionManager).execute(status -> {
                    // Le webhook tient les lignes avec le verrou que pose fulfillByPaymentIntent (for no key update),
                    // le temps que le retry arrive.
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

    // F2 : un retry après paiement ne rend pas le client secret d'une intention réglée et ne touche
    // pas à l'adresse d'une commande payée.
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

    // F2 : revenir dans la minute à un panier dont l'intention a été remplacée puis annulée repart sur
    // une intention neuve, au lieu de rendre le client secret d'une intention annulée.
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

    // M2 : huit annulations dans la même minute gardent une clé bornée et des retries identiques.
    @Test
    void repeatedlyCancelledAttempt_keepsBoundedStableKeysAndOneReservation() {
        // Ce scénario traverse huit maillons ; il lui faut davantage que les cinq secondes habituelles.
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

    // M3 : Stripe a encaissé mais le webhook retardé reste seul responsable de la confirmation.
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

    // M3 : un paiement en traitement ou autorisé garde toutes ses données sans confirmation inventée.
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

    // M3 : une intention annulée chez Stripe avec des lignes PENDING repart sans doubler la réservation.
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

    // M3 : même une intention annoncée annulée ne permet pas de rouvrir une commande réglée en base.
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

    // F5 : l'annulation chez Stripe d'une tentative remplacée reste acquise même si la réservation de
    // la nouvelle tentative échoue ensuite : l'intention annulée ne garde pas sa pièce.
    @Test
    void failedReservationAfterCancellingTheSupersededIntent_keepsTheCancellation() {
        awayFromMinuteEdge();
        Variant variant = listing("EUR", 8900, 2);
        String buyer = buyer();
        String first = intentId(checkout(buyer, variant, 1));
        // Deux lignes de la même déclinaison : chacune passe le contrôle rapide, la seconde réservation échoue.
        CartIntentRequest request = new CartIntentRequest(List.of(line(variant, 2), line(variant, 1)), address());

        assertThatThrownBy(() -> directSaleService.createCartPaymentIntent(buyer, request))
                .isInstanceOf(BillingValidationException.class);
        assertThat(stripe.status(first)).isEqualTo("canceled");
        assertThat(orderStatuses(first)).containsOnly("CANCELLED");
        assertThat(stock(variant)).isEqualTo(2);
    }

    // F4 : un paiement dont le règlement échoue au balayage n'empêche pas de régler les autres.
    @Test
    void sweepWithOneFailingPayment_stillSettlesTheOthers() {
        Variant healthy = listing("EUR", 8900, 1);
        Variant broken = listing("EUR", 8900, 1);
        String healthyIntent = intentId(checkout(buyer(), healthy, 1));
        String brokenIntent = intentId(checkout(buyer(), broken, 1));
        makeStale(healthyIntent);
        makeStale(brokenIntent);
        // La remise en rayon de cette pièce dépasse la capacité de la colonne : son règlement échoue en base.
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

    // F8 : un montant au-delà du maximum Stripe est un refus de la saisie (422), pas une panne (502).
    @Test
    void amountAboveTheStripeMaximum_isRefusedAsInvalid() {
        Variant variant = listing("EUR", 60_000_000, 2);

        assertThatThrownBy(() -> checkout(buyer(), variant, 2)).isInstanceOf(BillingValidationException.class);
        assertThat(stock(variant)).isEqualTo(2);
        assertThat(pendingOrders(variant)).isZero();
    }

    // S1 : une vente commise pendant que l'atelier enregistre son annonce n'est pas effacée par cet
    // enregistrement : la pièce vendue ne redevient pas achetable.
    @Test
    void saleDuringAnArtisanSave_isNotOverwrittenByTheSave() throws Exception {
        Variant variant = listing("EUR", 8900, 1);
        // L'atelier ne change que le SKU, avec la version qu'il vient de lire : aucune vente n'a encore eu lieu.
        UpdateProductRequest edit = edit(variant, List.of(
                new ProductVariantForm(variant.id(), "M", null, null, "SKU-NEW", 1, 0, 0L)));

        List<Outcome> outcomes = duringArtisanSave(variant, edit, () -> checkout(buyer(), variant, 1));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(stock(variant)).isZero();
        assertThat(pendingOrders(variant)).isEqualTo(1);
    }

    // S1 : un panier qui réserve deux déclinaisons de la même annonce pendant son enregistrement par
    // l'atelier aboutit, sans interblocage, et chaque déclinaison garde sa vente.
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
        // Le panier énumère les déclinaisons dans l'ordre inverse de leurs identifiants.
        List<CartIntentRequest.Line> lines = new ArrayList<>(List.of(line(medium, 1), line(large, 1)));
        lines.sort((a, b) -> b.variantId().toString().compareTo(a.variantId().toString()));
        String buyer = buyer();

        List<Outcome> outcomes = duringArtisanSave(medium, edit,
                () -> directSaleService.createCartPaymentIntent(buyer, new CartIntentRequest(lines, address())));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(stock(medium)).isEqualTo(1);
        assertThat(stock(large)).isEqualTo(1);
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

    // Crée un acheteur et renvoie son courriel pour le checkout.
    private String buyer() {
        UUID id = user("CONSUMER");
        return jdbc.queryForObject("select email from users where id = ?", String.class, id);
    }

    // Insère un utilisateur de test avec le rôle demandé.
    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, role, name) values (?, ?, '{noop}x', ?, 'IT')",
                id, role.toLowerCase() + "-" + id + "@lumiris.test", role);
        return id;
    }

    // Enregistrement complet de l'annonce par son atelier, avec les déclinaisons données.
    private UpdateProductRequest edit(Variant variant, List<ProductVariantForm> variants) {
        UUID dpp = jdbc.queryForObject("select dpp_form_id from marketplace_products where id = ?", UUID.class,
                variant.productId());
        return new UpdateProductRequest("Veste IT", null, null, "Lin", null, 8900, "EUR", 690, null, 0, 0,
                variants, List.of(), null, null, dpp, MarketplaceProductStatus.PUBLISHED);
    }

    // Lance l'enregistrement de l'atelier, le retient au moment d'écrire l'annonce (verrou sur sa ligne,
    // posé par une autre connexion), lance l'opération concurrente pendant ce temps, puis libère.
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

    // Attend qu'au moins `count` transactions de la base attendent un verrou.
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

    // Lance le checkout avec l'adresse de livraison habituelle du test.
    private PaymentIntentResponse checkout(String buyerEmail, Variant variant, int quantity) {
        return checkout(buyerEmail, variant, quantity, address());
    }

    // Construit une ligne de panier pour la déclinaison de test.
    private static CartIntentRequest.Line line(Variant variant, int quantity) {
        return new CartIntentRequest.Line(variant.productId(), variant.id(), quantity);
    }

    // Lance le vrai service de checkout avec l'adresse donnée.
    private PaymentIntentResponse checkout(String buyerEmail, Variant variant, int quantity,
                                           CartIntentRequest.ShippingAddress shipping) {
        return directSaleService.createCartPaymentIntent(buyerEmail,
                new CartIntentRequest(List.of(line(variant, quantity)), shipping));
    }

    // Rend l'adresse de livraison habituelle des scénarios.
    private static CartIntentRequest.ShippingAddress address() {
        return address("1 rue du Test");
    }

    // Construit l'adresse de livraison avec la rue demandée.
    private static CartIntentRequest.ShippingAddress address(String line1) {
        return new CartIntentRequest.ShippingAddress("Acheteur IT", line1, null, "75001", "Paris", "FR", null);
    }

    // La clé d'idempotence change à chaque minute : un test qui rejoue la même tentative ne doit pas
    // chevaucher ce changement.
    private static void awayFromMinuteEdge() {
        int second = LocalTime.now().getSecond();
        if (second >= 55) {
            pause((61 - second) * 1000L);
        }
    }

    // Attend la durée demandée en conservant une éventuelle interruption.
    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // Extrait l'identifiant de l'intention du client secret de test.
    private static String intentId(PaymentIntentResponse response) {
        return response.clientSecret().substring(0, response.clientSecret().indexOf("_secret_"));
    }

    // Livre un webhook signé au vrai service de réception.
    private Object deliver(String payload) {
        webhookService.handle(payload, signature(payload, WEBHOOK_SECRET));
        return payload;
    }

    // Construit l'événement de paiement réussi pour l'intention donnée.
    private static String paymentSucceeded(String intent) {
        return """
                {"id":"evt_%s","object":"event","api_version":"2024-06-20","type":"payment_intent.succeeded",
                 "data":{"object":{"id":"%s","object":"payment_intent","status":"succeeded",
                 "metadata":{"order_type":"marketplace"}}}}"""
                .formatted(UUID.randomUUID(), intent);
    }

    // Signe le webhook avec le secret et l'horodatage attendus par Stripe.
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

    // Relit les seules requêtes de création d'intention reçues par le faux Stripe.
    private List<FakeStripeApi.Request> creations() {
        return stripe.requests().stream()
                .filter(r -> r.method().equals("POST") && r.path().equals("/v1/payment_intents"))
                .toList();
    }

    // Vieillit les commandes pour les rendre éligibles au balayage des paiements abandonnés.
    private void makeStale(String intent) {
        jdbc.update("update marketplace_orders set created_at = now() - interval '25 hours' "
                + "where stripe_payment_intent_id = ?", intent);
    }

    // Relit le stock effectif de la déclinaison en base.
    private int stock(Variant variant) {
        return jdbc.queryForObject("select stock from marketplace_product_variants where id = ?", Integer.class,
                variant.id());
    }

    // Compte les commandes en attente de la déclinaison.
    private int pendingOrders(Variant variant) {
        return jdbc.queryForObject("select count(*) from marketplace_orders where variant_id = ? and status = 'PENDING'",
                Integer.class, variant.id());
    }

    // Relit les statuts des commandes rattachées à l'intention.
    private List<String> orderStatuses(String intent) {
        return jdbc.queryForList("select status from marketplace_orders where stripe_payment_intent_id = ?",
                String.class, intent);
    }

    // Compte les événements du type demandé sur les commandes de l'intention.
    private int events(String intent, String type) {
        return jdbc.queryForObject("select count(*) from marketplace_order_events e join marketplace_orders o "
                + "on o.id = e.order_id where o.stripe_payment_intent_id = ? and e.type = ?", Integer.class, intent, type);
    }

    // Relit les rues de livraison enregistrées pour l'intention.
    private List<String> shippingLines(String intent) {
        return jdbc.queryForList("select ship_to_line1 from marketplace_orders where stripe_payment_intent_id = ?",
                String.class, intent);
    }

    // Relit les numéros de facture enregistrés pour l'intention.
    private List<String> invoiceNumbers(String intent) {
        return jdbc.queryForList("select invoice_number from marketplace_orders where stripe_payment_intent_id = ?",
                String.class, intent);
    }

    // Compte les pièces ajoutées à la garde-robe pour l'intention.
    private int wardrobeItems(String intent) {
        return jdbc.queryForObject("select count(*) from wardrobe_items w join marketplace_orders o "
                + "on o.id = w.order_id where o.stripe_payment_intent_id = ?", Integer.class, intent);
    }
}
