package com.minoh.lumiris_backend.service.stripe;

import com.stripe.Stripe;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// API Stripe de test, servie par le serveur HTTP du JDK : le SDK réel y est redirigé par
// Stripe.overrideApiBase, ce qui fonctionne sur tous les threads (Mockito ne peut pas simuler les
// méthodes statiques avec le mock-maker « subclass » du projet). Elle reproduit la règle documentée
// des clés d'idempotence : même clé et mêmes paramètres → même réponse ; même clé et paramètres
// différents → erreur 400 idempotency_error.
final class FakeStripeApi implements AutoCloseable {

    record Request(String method, String path, String idempotencyKey, Map<String, String> params) {}

    private record Stored(Map<String, String> params, int status, String body) {}

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Stored> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, String> statusById = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile CyclicBarrier creationBarrier;
    private volatile boolean unavailable;

    FakeStripeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1/payment_intents", this::handle);
        server.start();
        Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort());
    }

    // Les réponses de création attendent qu'un nombre donné d'appels soit arrivé : les transactions
    // concurrentes ont alors toutes passé les contrôles, et reprennent ensemble avant qu'aucune n'ait
    // réservé ni validé.
    void holdCreationsUntil(int parties) {
        creationBarrier = new CyclicBarrier(parties);
    }

    // Simule un Stripe injoignable : toute requête répond 500 sans nouvelle tentative du SDK.
    void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    void setStatus(String paymentIntentId, String status) {
        statusById.put(paymentIntentId, status);
    }

    String status(String paymentIntentId) {
        return statusById.get(paymentIntentId);
    }

    List<Request> requests() {
        return List.copyOf(requests);
    }

    long count(String method, String pathPrefix) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).count();
    }

    void reset() {
        requests.clear();
        byIdempotencyKey.clear();
        statusById.clear();
        creationBarrier = null;
        unavailable = false;
    }

    @Override
    public void close() {
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Map<String, String> params = parseForm(exchange.getRequestBody());
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        requests.add(new Request(method, path, key, params));
        if (unavailable) {
            exchange.getResponseHeaders().add("Stripe-Should-Retry", "false");
            respond(exchange, 500, error("api_error", null, "Stripe indisponible (test)."));
            return;
        }
        String[] segments = path.substring(1).split("/");
        if ("POST".equals(method) && segments.length == 2) {
            create(exchange, key, params);
        } else if ("GET".equals(method) && segments.length == 3) {
            retrieve(exchange, segments[2]);
        } else if ("POST".equals(method) && segments.length == 4 && "cancel".equals(segments[3])) {
            cancel(exchange, segments[2]);
        } else {
            respond(exchange, 404, error("invalid_request_error", "resource_missing", "Route inconnue."));
        }
    }

    private void create(HttpExchange exchange, String key, Map<String, String> params) throws IOException {
        Stored response = decide(key, params);
        awaitBarrier();
        respond(exchange, response.status(), response.body());
    }

    // Décision atomique, comme chez Stripe : la première requête d'une clé crée le PaymentIntent, les
    // suivantes reçoivent la même réponse, ou une erreur si leurs paramètres diffèrent.
    private synchronized Stored decide(String key, Map<String, String> params) {
        Stored previous = key != null ? byIdempotencyKey.get(key) : null;
        if (previous != null) {
            return previous.params().equals(params) ? previous : new Stored(params, 400, error("idempotency_error", null,
                    "Keys for idempotent requests can only be used with the same parameters they were first used with."));
        }
        String id = "pi_test_" + sequence.incrementAndGet();
        statusById.put(id, "requires_payment_method");
        Stored created = new Stored(params, 200, paymentIntent(id, params));
        if (key != null) {
            byIdempotencyKey.put(key, created);
        }
        return created;
    }

    private void retrieve(HttpExchange exchange, String id) throws IOException {
        if (!statusById.containsKey(id)) {
            respond(exchange, 404, error("invalid_request_error", "resource_missing", "No such payment_intent."));
            return;
        }
        respond(exchange, 200, paymentIntent(id, Map.of()));
    }

    private void cancel(HttpExchange exchange, String id) throws IOException {
        String status = statusById.get(id);
        if (status == null) {
            respond(exchange, 404, error("invalid_request_error", "resource_missing", "No such payment_intent."));
            return;
        }
        if ("succeeded".equals(status) || "canceled".equals(status)) {
            respond(exchange, 400, error("invalid_request_error", "payment_intent_unexpected_state",
                    "This PaymentIntent's status is " + status + "."));
            return;
        }
        statusById.put(id, "canceled");
        respond(exchange, 200, paymentIntent(id, Map.of()));
    }

    private void awaitBarrier() {
        CyclicBarrier barrier = creationBarrier;
        if (barrier == null) {
            return;
        }
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Les créations concurrentes attendues ne sont pas arrivées", e);
        }
    }

    private String paymentIntent(String id, Map<String, String> params) {
        return "{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + statusById.get(id) + "\","
                + "\"client_secret\":\"" + id + "_secret_test\","
                + "\"amount\":" + params.getOrDefault("amount", "0") + ","
                + "\"currency\":\"" + params.getOrDefault("currency", "eur") + "\","
                + "\"metadata\":{}}";
    }

    private static String error(String type, String code, String message) {
        return "{\"error\":{\"type\":\"" + type + "\"," + (code != null ? "\"code\":\"" + code + "\"," : "")
                + "\"message\":\"" + message + "\"}}";
    }

    private static Map<String, String> parseForm(InputStream body) throws IOException {
        String raw = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = new TreeMap<>();
        if (raw.isBlank()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(name, value);
        }
        return params;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Request-Id", "req_test");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
