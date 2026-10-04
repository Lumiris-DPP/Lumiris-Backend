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

// Simule les remboursements et versements sans appeler Stripe.
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

    // Démarre le serveur local simulant les opérations financières.
    FakeOrderStripeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", this::handle);
        server.setExecutor(executor);
        server.start();
        Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort());
    }

    // Retient le remboursement simulé jusqu'à sa libération.
    void holdRefund() {
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    // Attend le remboursement simulé avec une durée bornée.
    boolean awaitRefund() throws InterruptedException {
        return entered.await(10, TimeUnit.SECONDS);
    }

    // Libère le remboursement retenu par le test.
    void releaseRefund() {
        if (release != null) release.countDown();
    }

    // Prépare un échec du prochain remboursement simulé.
    void failRefund(boolean value) {
        failRefund = value;
    }

    // Prépare un échec du prochain versement simulé.
    void failTransfer(boolean value) {
        failTransfer = value;
    }

    // Compte les remboursements enregistrés par le serveur simulé.
    int refunds() { return refunds.get(); }

    // Compte les reprises de versement enregistrées par le test.
    int reversals() { return reversals.get(); }

    // Compte les versements enregistrés par le serveur simulé.
    int transfers() { return transfers.get(); }

    // Réinitialise les compteurs et les erreurs du serveur simulé.
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

    // Ferme le serveur simulé et restaure la configuration Stripe.
    @Override
    public void close() {
        releaseRefund();
        server.stop(0);
        executor.shutdownNow();
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
    }

    // Simule les appels de versement, reprise et remboursement.
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

    // Envoie la réponse HTTP préparée par le test.
    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
