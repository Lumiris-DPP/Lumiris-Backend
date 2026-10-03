package com.minoh.lumiris_backend.service.stripe;

import com.stripe.Stripe;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Simule les remboursements et reversements Stripe sur un serveur HTTP local. */
final class FakeOrderStripeApi implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final Map<String, String> parameters = new ConcurrentHashMap<>();
    private final AtomicInteger refunds = new AtomicInteger();
    private final AtomicInteger reversals = new AtomicInteger();
    private final AtomicInteger transfers = new AtomicInteger();
    private volatile boolean failRefund;
    private volatile boolean failTransfer;
    private volatile CountDownLatch entered;
    private volatile CountDownLatch release;

    /** Démarre le serveur et redirige le SDK Stripe. */
    FakeOrderStripeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", this::handle);
        server.setExecutor(executor);
        server.start();
        Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Prépare une pause contrôlée lors du prochain remboursement. */
    void holdRefund() {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    /** Attend que le remboursement ait atteint Stripe. */
    boolean awaitRefund() throws InterruptedException {
        return entered.await(10, TimeUnit.SECONDS);
    }

    /** Autorise la réponse au remboursement en attente. */
    void releaseRefund() {
        if (release != null) release.countDown();
    }

    /** Rend le prochain remboursement indisponible sans reprise automatique du SDK. */
    void failRefund(boolean value) {
        failRefund = value;
    }

    /** Rend les reversements indisponibles pour vérifier leur reprise. */
    void failTransfer(boolean value) {
        failTransfer = value;
    }

    /** Compte les remboursements réellement créés. */
    int refunds() { return refunds.get(); }

    /** Compte les reprises de fonds réellement créées. */
    int reversals() { return reversals.get(); }

    /** Compte les reversements réellement créés. */
    int transfers() { return transfers.get(); }

    /** Efface les opérations du scénario précédent. */
    void reset() {
        responses.clear();
        parameters.clear();
        refunds.set(0);
        reversals.set(0);
        transfers.set(0);
        failRefund = false;
        failTransfer = false;
        entered = null;
        release = null;
    }

    /** Ferme le serveur et restaure l'adresse du SDK. */
    @Override
    public void close() {
        releaseRefund();
        server.stop(0);
        executor.shutdownNow();
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
    }

    /** Répond au SDK en conservant les effets sous leur clé d'idempotence. */
    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String params = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.equals("/v1/refunds") && entered != null) {
            entered.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) throw new IOException("Attente Stripe expirée");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
        if ((path.equals("/v1/refunds") && failRefund) || (path.equals("/v1/transfers") && failTransfer)) {
            exchange.getResponseHeaders().add("Stripe-Should-Retry", "false");
            respond(exchange, 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"Panne simulée\"}}");
            return;
        }
        if (exchange.getRequestMethod().equals("GET")) {
            String body = path.contains("payment_intents")
                    ? "{\"id\":\"pi_order\",\"object\":\"payment_intent\",\"latest_charge\":\"ch_order\"}"
                    : "{\"id\":\"tr_order\",\"object\":\"transfer\",\"reversals\":{\"object\":\"list\",\"url\":\"/v1/transfers/tr_order/reversals\",\"data\":[]}}";
            respond(exchange, 200, body);
            return;
        }
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        String previous = parameters.putIfAbsent(key, params);
        if (previous != null && !previous.equals(params)) {
            respond(exchange, 400, "{\"error\":{\"type\":\"idempotency_error\",\"message\":\"Paramètres différents\"}}");
            return;
        }
        String body = responses.computeIfAbsent(key, ignored -> {
            String object = path.equals("/v1/refunds") ? "refund" : path.endsWith("reversals") ? "transfer_reversal" : "transfer";
            AtomicInteger count = object.equals("refund") ? refunds : object.equals("transfer_reversal") ? reversals : transfers;
            return "{\"id\":\"" + object + "_" + count.incrementAndGet() + "\",\"object\":\"" + object + "\"}";
        });
        respond(exchange, 200, body);
    }

    /** Écrit une réponse JSON au client HTTP. */
    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
