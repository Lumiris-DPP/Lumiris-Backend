package com.minoh.lumiris_backend.service.stripe;

import com.stripe.Stripe;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final List<Map<String, Object>> reversalHistory = new ArrayList<>();
    private int reversalLimit = 900;
    private int reversedCents;
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

    // Fixe le montant réellement versé au vendeur.
    void limitReversals(int cents) { reversalLimit = cents; }

    // Lit le cumul réellement repris au vendeur.
    int reversedCents() { return reversedCents; }

    // Oublie les clés des reprises tout en conservant leurs réponses historiques.
    void expireReversalKeys() {
        responses.keySet().removeIf(key -> key.startsWith("reversal:"));
        parameters.keySet().removeIf(key -> key.startsWith("reversal:"));
    }

    // Ajoute une reprise historique sans référence d'opération.
    void historicalReversal(String id, int cents) {
        reversalHistory.addFirst(Map.of("id", id, "object", "transfer_reversal", "amount", cents, "metadata", Map.of()));
        reversedCents += cents;
    }

    // Compte les versements enregistrés par le serveur simulé.
    int transfers() { return transfers.get(); }

    // Réinitialise les compteurs et les erreurs du serveur simulé.
    void reset() {
        responses.clear();
        reversalHistory.clear();
        reversalLimit = 900;
        reversedCents = 0;
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
            Map<String, String> query = decode(exchange.getRequestURI().getRawQuery());
            int start = 0;
            if (query.containsKey("starting_after")) {
                while (start < reversalHistory.size() && !reversalHistory.get(start).get("id").equals(query.get("starting_after"))) start++;
                start++;
            }
            int end = Math.min(start + 10, reversalHistory.size());
            Map<String, Object> page = Map.of("object", "list", "url", "/v1/transfers/tr_order/reversals",
                    "data", reversalHistory.subList(start, end), "has_more", end < reversalHistory.size());
            String body = path.contains("payment_intents")
                    ? "{\"id\":\"pi_order\",\"object\":\"payment_intent\",\"latest_charge\":\"ch_order\"}"
                    : new ObjectMapper().writeValueAsString(path.endsWith("/reversals") ? page
                    : Map.of("id", "tr_order", "object", "transfer", "amount", reversalLimit,
                            "amount_reversed", reversedCents, "reversals", page));
            respond(exchange, 200, body);
            return;
        }
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        String previous = parameters.putIfAbsent(key, params);
        if (previous != null && !previous.equals(params)) {
            respond(exchange, 400, "{\"error\":{\"type\":\"idempotency_error\",\"message\":\"Paramètres différents\"}}");
            return;
        }
        Map<String, String> values = decode(params);
        int amount = Integer.parseInt(values.getOrDefault("amount", "0"));
        if (path.endsWith("/reversals") && !responses.containsKey(key) && reversedCents + amount > reversalLimit) {
            respond(exchange, 400, "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Reprise supérieure au reliquat\"}}");
            return;
        }
        String body = responses.computeIfAbsent(key, ignored -> {
            String object = path.equals("/v1/refunds") ? "refund" : path.endsWith("reversals") ? "transfer_reversal" : "transfer";
            AtomicInteger count = object.equals("refund") ? refunds : object.equals("transfer_reversal") ? reversals : transfers;
            String id = object + "_" + count.incrementAndGet();
            if (object.equals("transfer_reversal")) {
                Map<String, String> metadata = new LinkedHashMap<>();
                values.forEach((name, value) -> {
                    if (name.startsWith("metadata[")) metadata.put(name.substring(9, name.length() - 1), value);
                });
                reversalHistory.addFirst(Map.of("id", id, "object", object, "amount", amount, "metadata", metadata));
                reversedCents += amount;
                try {
                    return new ObjectMapper().writeValueAsString(reversalHistory.getFirst());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            return "{\"id\":\"" + id + "\",\"object\":\"" + object + "\",\"amount\":" + amount + "}";
        });
        respond(exchange, 200, body);
    }

    // Décode les paramètres envoyés par le SDK Stripe.
    private Map<String, String> decode(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded != null && !encoded.isEmpty()) {
            for (String pair : encoded.split("&")) {
                String[] parts = pair.split("=", 2);
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return values;
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
