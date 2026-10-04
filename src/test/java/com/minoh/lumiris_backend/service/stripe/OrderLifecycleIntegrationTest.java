package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.service.EmailOutboxDispatcher;

import com.minoh.lumiris_backend.marketplace.order.dto.in.RefundRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.DisputeResolutionRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ReturnRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ReturnDecisionRequest;
import com.minoh.lumiris_backend.marketplace.order.dto.in.ShipOrderRequest;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderActorType;
import com.minoh.lumiris_backend.exception.BillingException;
import com.minoh.lumiris_backend.exception.BillingValidationException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.service.BlockchainService;
import com.minoh.lumiris_backend.marketplace.order.service.BuyerOrderService;
import com.minoh.lumiris_backend.marketplace.order.service.SellerOrderService;
import com.minoh.lumiris_backend.marketplace.order.service.OrderLifecycleService;
import com.minoh.lumiris_backend.marketplace.order.scheduling.OrderScheduler;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Vérifie les étapes de commande sur une base isolée.
@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "stripe.secret-key=sk_test_dummy", "stripe.publishable-key=pk_test_dummy",
        "stripe.bootstrap-catalog=false", "stripe.products.solo=prod_dummy",
        "stripe.products.studio=prod_dummy", "stripe.products.maison=prod_dummy",
        "stripe.products.atelier-plus=prod_dummy", "stripe.products.local=prod_dummy",
        "blockchain.wallet.private-key=0x0000000000000000000000000000000000000000000000000000000000000001",
        "spring.cache.type=none", "spring.datasource.hikari.maximum-pool-size=8"
})
class OrderLifecycleIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));
    private static FakeOrderStripeApi stripe;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderLifecycleService lifecycle;
    @Autowired private BuyerOrderService buyerOrders;
    @Autowired private SellerOrderService sellerOrders;
    @Autowired private OrderScheduler scheduler;
    @Autowired private MarketplaceOrderRepository orders;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean
    private EmailOutboxDispatcher emailOutboxDispatcher;

    @MockitoBean private MinioClient minioClient;
    @MockitoBean private BlockchainService blockchainService;

    // Démarre le serveur local simulant les réponses Stripe.
    @BeforeAll
    static void startStripe() throws Exception { stripe = new FakeOrderStripeApi(); }

    // Ferme le serveur local simulant les réponses Stripe.
    @AfterAll
    static void stopStripe() { stripe.close(); }

    // Réinitialise les paiements simulés avant chaque test.
    @BeforeEach
    void resetStripe() {
        stripe.reset();
        jdbc.update("update marketplace_orders set status = 'COMPLETED', net_cents = 0");
    }

    // Vérifie le refus d'accès aux commandes d'un autre acheteur.
    @Test
    void foreignBuyer_isDenied() {
        Fixture f = fixture("SHIPPED");
        String stranger = email(user("CONSUMER"));
        assertThatThrownBy(() -> buyerOrders.getMyOrder(stranger, f.order())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> buyerOrders.getMyOrderGroup(stranger, f.intent())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> lifecycle.confirmDelivery(stranger, f.order())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> lifecycle.requestReturn(stranger, f.order(), new ReturnRequest("Motif", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(status(f)).isEqualTo("SHIPPED");
        assertThat(events(f, "DELIVERED")).isZero();
    }

    // Vérifie le refus d'accès aux commandes d'un autre atelier.
    @Test
    void foreignSeller_isDenied() {
        Fixture f = fixture("PAID");
        String stranger = email(user("ARTISAN"));
        assertThatThrownBy(() -> sellerOrders.get(stranger, f.order())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> lifecycle.ship(stranger, f.order(), shipping())).isInstanceOf(RoleNotAllowedException.class);
        assertThatThrownBy(() -> lifecycle.refund(stranger, f.order(), new RefundRequest(200, "Geste")))
                .isInstanceOf(RoleNotAllowedException.class);
        assertThat(status(f)).isEqualTo("PAID");
        assertThat(stripe.refunds()).isZero();
    }

    // Vérifie le refus des actions incompatibles avec la commande.
    @Test
    void forbiddenTransitions_areRejected() {
        Fixture f = fixture("PAID");
        assertThatThrownBy(() -> lifecycle.confirmDelivery(f.buyer(), f.order())).isInstanceOf(BillingValidationException.class);
        assertThatThrownBy(() -> lifecycle.requestReturn(f.buyer(), f.order(), new ReturnRequest("Motif", null)))
                .isInstanceOf(BillingValidationException.class);
        lifecycle.ship(f.seller(), f.order(), shipping());
        assertThatThrownBy(() -> lifecycle.ship(f.seller(), f.order(), shipping())).isInstanceOf(BillingValidationException.class);
        assertThat(events(f, "SHIPPED")).isEqualTo(1);
    }

    // Vérifie un seul versement après expédition et livraison.
    @Test
    void shippingThenDelivery_releasesFundsOnce() {
        Fixture f = fixture("PAID");
        lifecycle.ship(f.seller(), f.order(), shipping());
        lifecycle.confirmDelivery(f.buyer(), f.order());
        lifecycle.retryRelease(load(f));
        assertThat(status(f)).isEqualTo("DELIVERED");
        assertThat(events(f, "SHIPPED")).isEqualTo(1);
        assertThat(events(f, "DELIVERED")).isEqualTo(1);
        assertThat(events(f, "FUNDS_RELEASED")).isEqualTo(1);
        assertThat(stripe.transfers()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select return_deadline > delivered_at from marketplace_orders where id = ?", Boolean.class, f.order())).isTrue();
    }

    // Vérifie le remboursement et le stock après un retour accepté.
    @Test
    void acceptedReturn_refundsAndRestocksOnce() {
        Fixture f = fixture("SHIPPED");
        lifecycle.confirmDelivery(f.buyer(), f.order());
        lifecycle.requestReturn(f.buyer(), f.order(), new ReturnRequest("Taille", null));
        lifecycle.decideReturn(f.seller(), f.order(), new ReturnDecisionRequest(true, "Accord", null));
        lifecycle.markReturnReceived(f.seller(), f.order());
        RefundRequest request = new RefundRequest(null, "Retour", UUID.randomUUID());
        lifecycle.refund(f.seller(), f.order(), request);
        lifecycle.refund(f.seller(), f.order(), request);
        assertThat(status(f)).isEqualTo("REFUNDED");
        assertThat(refunded(f)).isEqualTo(1000);
        assertThat(stock(f)).isEqualTo(1);
        assertThat(wardrobe(f)).isZero();
        assertThat(stripe.refunds()).isEqualTo(1);
        assertThat(stripe.reversals()).isEqualTo(1);
        for (String type : List.of("RETURN_REQUESTED", "RETURN_APPROVED", "RETURN_RECEIVED", "REFUNDED")) {
            assertThat(events(f, type)).isEqualTo(1);
        }
    }

    // Vérifie le refus de retour sans remboursement ni remise en stock.
    @Test
    void refusedReturn_recordsDecisionWithoutRefundOrRestock() {
        Fixture f = fixture("SHIPPED");
        lifecycle.requestReturn(f.buyer(), f.order(), new ReturnRequest("Taille", null));
        lifecycle.decideReturn(f.seller(), f.order(), new ReturnDecisionRequest(false, "Motif du refus", null));

        assertThat(status(f)).isEqualTo("RETURN_REFUSED");
        assertThat(events(f, "RETURN_REFUSED")).isEqualTo(1);
        assertThat(events(f, "RETURN_APPROVED")).isZero();
        assertThat(jdbc.queryForObject("select return_decision_note from marketplace_orders where id = ?",
                String.class, f.order())).isEqualTo("Motif du refus");
        assertThat(jdbc.queryForObject("select return_decided_at is not null from marketplace_orders where id = ?",
                Boolean.class, f.order())).isTrue();
        assertThat(refunded(f)).isZero();
        assertThat(stock(f)).isZero();
        assertThat(wardrobe(f)).isEqualTo(1);
        assertThat(stripe.refunds()).isZero();
        assertThatThrownBy(() -> lifecycle.decideReturn(f.seller(), f.order(),
                new ReturnDecisionRequest(true, "Autre décision", null))).isInstanceOf(BillingValidationException.class);
        assertThat(status(f)).isEqualTo("RETURN_REFUSED");
        assertThat(events(f, "RETURN_REFUSED")).isEqualTo(1);
    }

    // Vérifie le remboursement unique du litige décidé par la plateforme.
    @Test
    void disputeResolution_refundsOnceWithPlatformStatusAndRestock() {
        Fixture f = fixture("DELIVERED");
        jdbc.update("update marketplace_orders set dispute_status = 'OPEN' where id = ?", f.order());
        String admin = email(user("ADMIN"));
        DisputeResolutionRequest request = new DisputeResolutionRequest("Accord plateforme", 300);

        lifecycle.resolveDispute(admin, f.order(), request);

        assertThat(status(f)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select dispute_status from marketplace_orders where id = ?",
                String.class, f.order())).isEqualTo("RESOLVED");
        assertThat(events(f, "DISPUTE_RESOLVED")).isEqualTo(1);
        assertThat(events(f, "REFUNDED")).isEqualTo(1);
        assertThat(refunded(f)).isEqualTo(300);
        assertThat(stock(f)).isEqualTo(1);
        assertThat(wardrobe(f)).isEqualTo(1);
        assertThat(stripe.refunds()).isEqualTo(1);
        assertThatThrownBy(() -> lifecycle.resolveDispute(admin, f.order(), request))
                .isInstanceOf(BillingValidationException.class);
        assertThat(stripe.refunds()).isEqualTo(1);
        assertThat(stock(f)).isEqualTo(1);
    }

    // Vérifie les remboursements successifs et une seule remise en stock.
    @Test
    void equalPartialRefunds_thenTotal_keepAmountsAndStockCorrect() {
        Fixture f = fixture("DELIVERED");
        jdbc.update("update marketplace_orders set stripe_transfer_id = 'tr_order' where id = ?", f.order());
        lifecycle.refund(f.seller(), f.order(), new RefundRequest(200, "Geste"));
        assertThat(wardrobe(f)).isEqualTo(1);
        lifecycle.refund(f.seller(), f.order(), new RefundRequest(200, "Geste"));
        assertThat(refunded(f)).isEqualTo(400);
        assertThat(stock(f)).isEqualTo(1);
        RefundRequest total = new RefundRequest(null, "Solde", UUID.randomUUID());
        lifecycle.refund(f.seller(), f.order(), total);
        lifecycle.refund(f.seller(), f.order(), total);
        assertThat(refunded(f)).isEqualTo(1000);
        assertThat(wardrobe(f)).isZero();
        assertThat(stock(f)).isEqualTo(1);
        assertThat(stripe.refunds()).isEqualTo(3);
        assertThat(stripe.reversals()).isEqualTo(3);
        assertThat(events(f, "REFUNDED")).isEqualTo(3);
    }

    // Vérifie le refus d'une référence de remboursement aux paramètres différents.
    @Test
    void reusedOperationWithDifferentParameters_isRejected() {
        Fixture f = fixture("PAID");
        UUID operation = UUID.randomUUID();
        lifecycle.refund(f.seller(), f.order(), new RefundRequest(200, "Geste", operation));
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), new RefundRequest(300, "Geste", operation)))
                .isInstanceOf(BillingValidationException.class);
        assertThat(refunded(f)).isEqualTo(200);
        assertThat(stripe.refunds()).isEqualTo(1);
    }

    // Vérifie qu'un remboursement invalide laisse la commande inchangée.
    @Test
    void invalidRefunds_haveNoEffects() {
        Fixture paid = fixture("PAID");
        Fixture pending = fixture("PENDING");
        assertThatThrownBy(() -> lifecycle.refund(paid.seller(), paid.order(), new RefundRequest(1001, "Geste")))
                .isInstanceOf(BillingValidationException.class);
        assertThatThrownBy(() -> lifecycle.refund(pending.seller(), pending.order(), new RefundRequest(200, "Geste")))
                .isInstanceOf(BillingValidationException.class);
        assertThat(stripe.refunds()).isZero();
        assertThat(stock(paid)).isZero();
    }

    // Vérifie une seule reprise du versement malgré l'échec du remboursement.
    @Test
    void refundFailureAfterReversal_retryDoesNotReverseTwice() {
        Fixture f = fixture("DELIVERED");
        jdbc.update("update marketplace_orders set stripe_transfer_id = 'tr_order' where id = ?", f.order());
        RefundRequest request = new RefundRequest(200, "Geste", UUID.randomUUID());
        stripe.failRefund(true);
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), request)).isInstanceOf(BillingException.class);
        assertThat(refunded(f)).isZero();
        assertThat(stock(f)).isZero();
        stripe.failRefund(false);
        lifecycle.refund(f.seller(), f.order(), request);
        lifecycle.refund(f.seller(), f.order(), request);
        assertThat(stripe.reversals()).isEqualTo(1);
        assertThat(stripe.refunds()).isEqualTo(1);
        assertThat(refunded(f)).isEqualTo(200);
    }

    // Vérifie le retry après remboursement Stripe et annulation en base.
    @Test
    void stripeSuccessThenDatabaseRollback_retryIsIdempotent() {
        Fixture f = fixture("PAID");
        RefundRequest request = new RefundRequest(200, "Geste", UUID.randomUUID());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            lifecycle.refund(f.seller(), f.order(), request);
            throw new IllegalStateException("Rollback après Stripe");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(refunded(f)).isZero();
        assertThat(stock(f)).isZero();
        lifecycle.refund(f.seller(), f.order(), request);
        assertThat(stripe.refunds()).isEqualTo(1);
        assertThat(refunded(f)).isEqualTo(200);
        assertThat(stock(f)).isEqualTo(1);
        assertThat(events(f, "REFUNDED")).isEqualTo(1);
    }

    // Vérifie un seul effet pour les remboursements rejoués simultanément.
    @Test
    void concurrentRefundRetries_haveOneEffect() throws Exception {
        Fixture f = fixture("PAID");
        RefundRequest request = new RefundRequest(200, "Geste", UUID.randomUUID());
        raceRefunds(f, request, request);
        assertThat(refunded(f)).isEqualTo(200);
        assertThat(stock(f)).isEqualTo(1);
        assertThat(stripe.refunds()).isEqualTo(1);
        assertThat(events(f, "REFUNDED")).isEqualTo(1);
    }

    // Vérifie que deux remboursements distincts conservent leurs deux effets.
    @Test
    void concurrentDistinctRefunds_keepBothOperations() throws Exception {
        Fixture f = fixture("PAID");
        raceRefunds(f, new RefundRequest(200, "Geste", UUID.randomUUID()), new RefundRequest(200, "Geste", UUID.randomUUID()));
        assertThat(refunded(f)).isEqualTo(400);
        assertThat(stock(f)).isEqualTo(1);
        assertThat(stripe.refunds()).isEqualTo(2);
        assertThat(events(f, "REFUNDED")).isEqualTo(2);
    }

    // Vérifie que le traitement périodique relit le retour courant.
    @Test
    void staleSchedulerSnapshot_doesNotOverwriteReturn() {
        Fixture f = fixture("SHIPPED");
        MarketplaceOrder stale = load(f);
        lifecycle.requestReturn(f.buyer(), f.order(), new ReturnRequest("Taille", null));
        lifecycle.markDelivered(stale, OrderActorType.SYSTEM);
        assertThat(status(f)).isEqualTo("RETURN_REQUESTED");
        assertThat(stripe.transfers()).isZero();
        assertThat(events(f, "DELIVERED")).isZero();
    }

    // Vérifie un seul versement après confirmations de livraison concurrentes.
    @Test
    void concurrentDeliveryConfirmations_releaseFundsOnce() throws Exception {
        Fixture f = fixture("SHIPPED");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from marketplace_orders where id = ? for no key update", UUID.class, f.order());
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> lifecycle.confirmDelivery(f.buyer(), f.order())));
                }
                awaitWaitingTransactions(2);
            });
            int accepted = 0;
            int refused = 0;
            for (Future<?> future : futures) {
                try {
                    future.get(20, TimeUnit.SECONDS);
                    accepted++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BillingValidationException.class);
                    refused++;
                }
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(refused).isEqualTo(1);
            assertThat(events(f, "DELIVERED")).isEqualTo(1);
            assertThat(events(f, "FUNDS_RELEASED")).isEqualTo(1);
            assertThat(stripe.transfers()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // Vérifie les échéances automatiques sans modifier les litiges ouverts.
    @Test
    void schedulerDeliversAndCompletesOnce_preservingDisputes() {
        Fixture delivered = fixture("SHIPPED");
        Fixture completed = fixture("DELIVERED");
        Fixture disputed = fixture("SHIPPED");
        jdbc.update("update marketplace_orders set shipped_at = now() - interval '60 days', "
                + "delivered_at = now() - interval '60 days', return_deadline = now() - interval '46 days' "
                + "where id in (?,?,?)", delivered.order(), completed.order(), disputed.order());
        jdbc.update("update marketplace_orders set dispute_status = 'OPEN' where id = ?", disputed.order());
        scheduler.advanceStaleOrders();
        scheduler.advanceStaleOrders();
        assertThat(status(delivered)).isEqualTo("DELIVERED");
        assertThat(status(completed)).isEqualTo("COMPLETED");
        assertThat(status(disputed)).isEqualTo("SHIPPED");
        assertThat(events(delivered, "DELIVERED")).isEqualTo(1);
        assertThat(events(completed, "COMPLETED")).isEqualTo(1);
        assertThat(events(disputed, "DELIVERED")).isZero();
    }

    // Vérifie une seule remise en stock d'une réservation abandonnée.
    @Test
    void abandonedOrderRetries_restockOnlyOnce() {
        Fixture f = fixture("PENDING");
        MarketplaceOrder first = load(f);
        MarketplaceOrder second = load(f);
        lifecycle.cancelAbandoned(first);
        lifecycle.cancelAbandoned(second);
        assertThat(status(f)).isEqualTo("CANCELLED");
        assertThat(stock(f)).isEqualTo(1);
        assertThat(events(f, "CANCELLED")).isEqualTo(1);
    }

    // Vérifie le nouvel essai d'un versement vendeur en échec.
    @Test
    void failedPayout_isRetriedBySchedulerOnce() {
        Fixture f = fixture("SHIPPED");
        stripe.failTransfer(true);
        lifecycle.confirmDelivery(f.buyer(), f.order());
        assertThat(status(f)).isEqualTo("DELIVERED");
        assertThat(stripe.transfers()).isZero();
        stripe.failTransfer(false);
        scheduler.remindAndRetry();
        scheduler.remindAndRetry();
        assertThat(stripe.transfers()).isEqualTo(1);
        assertThat(events(f, "FUNDS_RELEASED")).isEqualTo(1);
    }

    // Vérifie le remboursement et le stock d'une commande annulée.
    @Test
    void cancellation_refundsAndRestocksOnce() {
        Fixture f = fixture("PAID");
        lifecycle.cancel(f.buyer(), f.order(), "Erreur");
        assertThatThrownBy(() -> lifecycle.cancel(f.buyer(), f.order(), "Erreur")).isInstanceOf(BillingValidationException.class);
        assertThat(status(f)).isEqualTo("CANCELLED");
        assertThat(refunded(f)).isEqualTo(1000);
        assertThat(stock(f)).isEqualTo(1);
        assertThat(stripe.refunds()).isEqualTo(1);
    }

    // Croise les remboursements sous verrou avec des attentes bornées.
    private void raceRefunds(Fixture f, RefundRequest first, RefundRequest second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        stripe.holdRefund();
        try {
            Future<?> a = pool.submit(() -> lifecycle.refund(f.seller(), f.order(), first));
            assertThat(stripe.awaitRefund()).isTrue();
            Future<?> b = pool.submit(() -> lifecycle.refund(f.seller(), f.order(), second));
            awaitWaitingTransactions(1);
            assertThat(b.isDone()).isFalse();
            stripe.releaseRefund();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        } finally {
            stripe.releaseRefund();
            pool.shutdownNow();
        }
    }

    // Attend les transactions bloquées sans dépasser la borne du test.
    private void awaitWaitingTransactions(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int waiting = 0;
        while (System.nanoTime() < deadline && waiting < expected) {
            waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() "
                    + "and wait_event_type = 'Lock'", Integer.class);
            if (waiting < expected) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
        assertThat(waiting).as("Transactions en attente d'un verrou PostgreSQL").isGreaterThanOrEqualTo(expected);
    }

    // Regroupe les références d'une commande créée pour le test.
    private record Fixture(UUID order, UUID variant, String buyer, String seller, String intent) {}

    // Vérifie deux remboursements égaux et leur rejeu sans dépasser le versement.
    @Test
    void roundedHalves_refund100AndReverse95() {
        Fixture f = roundedFixture();
        RefundRequest first = new RefundRequest(50, "Geste", UUID.randomUUID());
        RefundRequest second = new RefundRequest(50, "Geste", UUID.randomUUID());
        lifecycle.refund(f.seller(), f.order(), first);
        assertThat(stripe.reversedCents()).isEqualTo(48);
        lifecycle.refund(f.seller(), f.order(), first);
        lifecycle.refund(f.seller(), f.order(), second);
        lifecycle.refund(f.seller(), f.order(), second);
        assertThat(refunded(f)).isEqualTo(100);
        assertThat(stripe.reversedCents()).isEqualTo(95);
        assertThat(stripe.refunds()).isEqualTo(2);
        assertThat(stock(f)).isEqualTo(1);
        assertThat(wardrobe(f)).isZero();
    }

    // Vérifie le cumul arrondi pour plusieurs découpages et ordres.
    @Test
    void roundedFractions_preserveCumulativeAmounts() {
        for (List<Integer> amounts : List.of(List.of(1, 49, 1, 49), List.of(49, 1, 49, 1),
                List.of(33, 33, 34), List.of(34, 33, 33), java.util.Collections.nCopies(100, 1))) {
            stripe.reset();
            Fixture f = roundedFixture();
            int total = 0;
            for (int amount : amounts) {
                lifecycle.refund(f.seller(), f.order(), new RefundRequest(amount, "Fraction", UUID.randomUUID()));
                total += amount;
                assertThat(refunded(f)).isEqualTo(total);
                assertThat(stripe.reversedCents()).isEqualTo(Math.round(95.0 * total / 100));
            }
            assertThat(stripe.reversedCents()).isEqualTo(95);
        }
    }

    // Vérifie le retry après reprise réussie et remboursement refusé.
    @Test
    void roundedReversalThenFailure_retryKeepsHistoricalAmount() {
        Fixture f = roundedFixture();
        RefundRequest first = new RefundRequest(50, "Geste", UUID.randomUUID());
        stripe.failRefund(true);
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), first)).isInstanceOf(BillingException.class);
        assertThat(refunded(f)).isZero();
        assertThat(stripe.reversedCents()).isEqualTo(48);
        stripe.failRefund(false);
        stripe.expireReversalKeys();
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Geste", UUID.randomUUID())))
                .isInstanceOf(BillingValidationException.class);
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), new RefundRequest(49, "Geste", first.operationId())))
                .isInstanceOf(BillingValidationException.class);
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Autre motif", first.operationId())))
                .isInstanceOf(BillingValidationException.class);
        lifecycle.refund(f.seller(), f.order(), first);
        lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Solde", UUID.randomUUID()));
        assertThat(refunded(f)).isEqualTo(100);
        assertThat(stripe.reversedCents()).isEqualTo(95);
        assertThat(stripe.reversals()).isEqualTo(2);
    }

    // Vérifie le retry du reliquat après un rollback SQL complet.
    @Test
    void roundedFinalRefundRollback_retryDoesNotChangeReversal() {
        Fixture f = roundedFixture();
        lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Premier", UUID.randomUUID()));
        RefundRequest second = new RefundRequest(50, "Solde", UUID.randomUUID());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            lifecycle.refund(f.seller(), f.order(), second);
            throw new IllegalStateException("Rollback après Stripe");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(refunded(f)).isEqualTo(50);
        assertThat(stripe.reversedCents()).isEqualTo(95);
        lifecycle.refund(f.seller(), f.order(), second);
        assertThat(refunded(f)).isEqualTo(100);
        assertThat(stripe.reversedCents()).isEqualTo(95);
        assertThat(stripe.reversals()).isEqualTo(2);
        assertThat(stripe.refunds()).isEqualTo(2);
    }

    // Vérifie le reliquat réel d'une ancienne reprise déjà enregistrée.
    @Test
    void historicalPartialReversal_usesActualRemainder() {
        for (int alreadyReversed : List.of(47, 48, 49)) {
            stripe.reset();
            Fixture f = roundedFixture();
            stripe.historicalReversal("trr_old", alreadyReversed);
            jdbc.update("update marketplace_orders set refunded_cents=50, stripe_transfer_reversal_id='trr_old' where id=?", f.order());
            lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Solde", UUID.randomUUID()));
            assertThat(refunded(f)).isEqualTo(100);
            assertThat(stripe.reversedCents()).isEqualTo(95);
        }
    }

    // Vérifie les frais de port et les fractions sans reprise supplémentaire.
    @Test
    void fractionalRefunds_includeShippingAndZeroReversals() {
        for (int net : List.of(1, 2, 95, 99)) {
            stripe.reset();
            Fixture f = roundedFixture();
            jdbc.update("update marketplace_orders set net_cents=?, commission_cents=?, shipping_cents=7 where id=?", net, 100 - net, f.order());
            stripe.limitReversals(net);
            int total = 0;
            for (int amount : List.of(1, 10, 23, 73)) {
                lifecycle.refund(f.seller(), f.order(), new RefundRequest(amount, "Fraction", UUID.randomUUID()));
                total += amount;
                assertThat(refunded(f)).isEqualTo(total);
                assertThat(stripe.reversedCents()).isEqualTo(Math.round((double) net * total / 107));
            }
            assertThat(refunded(f)).isEqualTo(107);
            assertThat(stripe.reversedCents()).isEqualTo(net);
        }
    }

    // Vérifie la conservation du repère historique lors d'une reprise nulle.
    @Test
    void historicalReversal_zeroRemainderKeepsCommittedReference() {
        Fixture f = roundedFixture();
        jdbc.update("update marketplace_orders set net_cents=1, commission_cents=99, refunded_cents=50, stripe_transfer_reversal_id='trr_old' where id=?", f.order());
        stripe.limitReversals(1);
        stripe.historicalReversal("trr_old", 1);
        lifecycle.refund(f.seller(), f.order(), new RefundRequest(1, "Fraction", UUID.randomUUID()));
        lifecycle.refund(f.seller(), f.order(), new RefundRequest(49, "Solde", UUID.randomUUID()));
        assertThat(refunded(f)).isEqualTo(100);
        assertThat(stripe.reversedCents()).isEqualTo(1);
        assertThat(stripe.reversals()).isZero();
    }

    // Vérifie le refus d'une ancienne reprise inconnue plus récente que la base.
    @Test
    void historicalUncommittedReversal_doesNotUseCommittedRemainder() {
        Fixture f = roundedFixture();
        stripe.historicalReversal("trr_old", 48);
        stripe.historicalReversal("trr_pending", 47);
        jdbc.update("update marketplace_orders set refunded_cents=50, stripe_transfer_reversal_id='trr_old' where id=?", f.order());
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Solde", UUID.randomUUID())))
                .isInstanceOf(BillingValidationException.class).hasMessageContaining("rapprochement");
        assertThat(refunded(f)).isEqualTo(50);
        assertThat(stripe.refunds()).isZero();
    }

    // Refuse une ancienne reprise sans preuve de rattachement.
    @Test
    void historicalOlderUnidentifiedReversal_requiresReconciliation() {
        Fixture f = roundedFixture();
        stripe.historicalReversal("trr_unknown", 10);
        stripe.historicalReversal("trr_old", 38);
        jdbc.update("update marketplace_orders set refunded_cents=50, stripe_transfer_reversal_id='trr_old' where id=?", f.order());
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Solde", UUID.randomUUID())))
                .isInstanceOf(BillingValidationException.class).hasMessageContaining("rapprochement");
        assertThat(refunded(f)).isEqualTo(50);
        assertThat(stripe.refunds()).isZero();
    }

    // Vérifie le refus explicite d'une ancienne reprise impossible à rattacher.
    @Test
    void unidentifiedHistoricalReversal_requiresReconciliation() {
        Fixture f = roundedFixture();
        stripe.historicalReversal("trr_unknown", 48);
        assertThatThrownBy(() -> lifecycle.refund(f.seller(), f.order(), new RefundRequest(50, "Geste", UUID.randomUUID())))
                .isInstanceOf(BillingValidationException.class);
        assertThat(refunded(f)).isZero();
        assertThat(stripe.refunds()).isZero();
        assertThat(stripe.reversedCents()).isEqualTo(48);
    }

    // Vérifie les remboursements concurrents avec un versement borné.
    @Test
    void roundedConcurrentRefunds_keepBothOperations() throws Exception {
        Fixture f = roundedFixture();
        raceRefunds(f, new RefundRequest(50, "Geste", UUID.randomUUID()), new RefundRequest(50, "Geste", UUID.randomUUID()));
        assertThat(refunded(f)).isEqualTo(100);
        assertThat(stripe.reversedCents()).isEqualTo(95);
        assertThat(stripe.refunds()).isEqualTo(2);
    }

    // Prépare une commande de 100 centimes avec un versement de 95.
    private Fixture roundedFixture() {
        Fixture f = fixture("DELIVERED");
        jdbc.update("update marketplace_orders set amount_total_cents=100, shipping_cents=0, net_cents=95, commission_cents=5, stripe_transfer_id='tr_order' where id=?", f.order());
        stripe.limitReversals(95);
        return f;
    }

    // Crée une commande et ses pièces dans la base isolée.
    private Fixture fixture(String status) {
        UUID buyer = user("CONSUMER");
        UUID seller = user("ARTISAN");
        UUID profile = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        String intent = "pi_" + order;
        jdbc.update("insert into artisan_profiles(id,user_id,display_name,slug) values (?,?,'Atelier',?)", profile, seller, "atelier-" + profile);
        jdbc.update("insert into seller_accounts(user_id,stripe_account_id,charges_enabled,payouts_enabled,onboarding_completed) "
                + "values (?, ?, true, true, true)", seller, "acct_" + seller);
        jdbc.update("insert into marketplace_products(id,artisan_profile_id,name,price_cents,currency,status) "
                + "values (?,?,'Veste',1000,'EUR','PUBLISHED')", product, profile);
        jdbc.update("insert into marketplace_product_variants(id,product_id,size_label,stock,position) values (?,?,'M',0,0)", variant, product);
        jdbc.update("insert into marketplace_orders(id,product_id,variant_id,buyer_user_id,seller_user_id,amount_total_cents,net_cents,status,"
                + "stripe_payment_intent_id,shipped_at,delivered_at,return_deadline) values (?,?,?,?,?,1000,900,?,?,now(),now(),now()+interval '14 days')",
                order, product, variant, buyer, seller, status, intent);
        jdbc.update("insert into wardrobe_items(id,user_id,order_id) values (?,?,?)", UUID.randomUUID(), buyer, order);
        return new Fixture(order, variant, email(buyer), email(seller), intent);
    }

    // Crée un utilisateur de test avec le rôle demandé.
    private UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,role,name) values (?,?,'{noop}x',?,'Test')", id, id + "@lumiris.test", role);
        return id;
    }

    // Retrouve l'adresse de l'utilisateur créé pour le test.
    private String email(UUID id) { return jdbc.queryForObject("select email from users where id = ?", String.class, id); }

    // Compose les informations de suivi utilisées par le test.
    private ShipOrderRequest shipping() { return new ShipOrderRequest("Poste", "TRACK", "https://example.test/TRACK"); }

    // Relit la commande créée dans la base isolée.
    private MarketplaceOrder load(Fixture f) { return orders.findById(f.order()).orElseThrow(); }

    // Lit l'état courant de la commande dans la base isolée.
    private String status(Fixture f) { return jdbc.queryForObject("select status from marketplace_orders where id = ?", String.class, f.order()); }

    // Lit le montant remboursé sur la commande testée.
    private int refunded(Fixture f) { return jdbc.queryForObject("select refunded_cents from marketplace_orders where id = ?", Integer.class, f.order()); }

    // Lit le stock de la déclinaison dans la base isolée.
    private int stock(Fixture f) { return jdbc.queryForObject("select stock from marketplace_product_variants where id = ?", Integer.class, f.variant()); }

    // Compte les événements du type demandé pour la commande.
    private int events(Fixture f, String type) { return jdbc.queryForObject("select count(*) from marketplace_order_events where order_id = ? and type = ?", Integer.class, f.order(), type); }

    // Compte les pièces acquises depuis la commande testée.
    private int wardrobe(Fixture f) { return jdbc.queryForObject("select count(*) from wardrobe_items where order_id = ?", Integer.class, f.order()); }
}
