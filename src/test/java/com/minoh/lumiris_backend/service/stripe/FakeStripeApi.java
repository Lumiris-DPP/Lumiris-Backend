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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Simule les paiements du panier sans appeler Stripe.
final class FakeStripeApi implements AutoCloseable {

    private static final long MAX_AMOUNT = 99_999_999L;

    // Conserve les paramètres d'un appel de paiement simulé.
    record Request(String method, String path, String idempotencyKey, Map<String, String> params) {}

    // Conserve le paiement simulé et ses paramètres d'origine.
    private record Stored(Map<String, String> params, int status, String body) {}

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Stored> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, String> statusById = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile CyclicBarrier creationBarrier;
    private volatile CyclicBarrier cancellationBarrier;
    private volatile Runnable cancellationAction;
    private volatile long processingMillis;
    private volatile boolean unavailable;

    // Démarre le serveur local simulant les paiements Stripe.
    FakeStripeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/v1/payment_intents", this::handle);
        server.start();
        Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort());
    }

    // Synchronise un nombre fixé de créations de paiement simulées.
    void holdCreationsUntil(int parties) {
        creationBarrier = new CyclicBarrier(parties);
    }

    // Synchronise un nombre fixé d'annulations de paiement simulées.
    void holdCancellationsUntil(int parties) {
        cancellationBarrier = new CyclicBarrier(parties);
    }

    // Prépare l'action exécutée pendant la prochaine annulation simulée.
    void onNextCancellation(Runnable action) {
        cancellationAction = action;
    }

    // Retarde les créations de paiement pour le test concurrent.
    void slowCreations(long millis) {
        processingMillis = millis;
    }

    // Configure une panne de réponse du serveur simulé.
    void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    // Modifie l'état d'un paiement conservé par le test.
    void setStatus(String paymentIntentId, String status) {
        statusById.put(paymentIntentId, status);
    }

    // Lit l'état du paiement conservé par le test.
    String status(String paymentIntentId) {
        return statusById.get(paymentIntentId);
    }

    // Liste les appels reçus par le serveur simulé.
    List<Request> requests() {
        return List.copyOf(requests);
    }

    // Compte les appels reçus sur la route demandée.
    long count(String method, String pathPrefix) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).count();
    }

    // Réinitialise les paiements, appels et attentes du serveur simulé.
    void reset() {
        requests.clear();
        byIdempotencyKey.clear();
        statusById.clear();
        inFlight.clear();
        creationBarrier = null;
        cancellationBarrier = null;
        cancellationAction = null;
        processingMillis = 0;
        unavailable = false;
    }

    // Ferme le serveur simulé et restaure la configuration Stripe.
    @Override
    public void close() {
        Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
        server.stop(0);
    }

    // Enregistre l'appel reçu et prépare la réponse Stripe simulée.
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

    // Crée ou retrouve un paiement avec la même clé.
    private void create(HttpExchange exchange, String key, Map<String, String> params) throws IOException {
        if (key != null && key.length() > 255) {
            respond(exchange, 400, error("invalid_request_error", null, "Idempotency key exceeds 255 characters."));
            return;
        }
        if (Long.parseLong(params.getOrDefault("amount", "0")) > MAX_AMOUNT) {
            respond(exchange, 400, error("invalid_request_error", "amount_too_large",
                    "Amount must be no more than €999,999.99"));
            return;
        }
        Stored response = decide(key, params);
        if (response == null) {
            exchange.getResponseHeaders().add("Stripe-Should-Retry", "true");
            respond(exchange, 409, error("idempotency_error", "idempotency_key_in_use",
                    "There is currently another in-progress request using this Stripe-Idempotency-Key."));
            return;
        }
        awaitBarrier();
        respond(exchange, response.status(), response.body());
    }

    // Exécute l'action concurrente préparée pour le paiement simulé.
    private Stored decide(String key, Map<String, String> params) {
        synchronized (this) {
            Stored previous = key != null ? byIdempotencyKey.get(key) : null;
            if (previous != null) {
                return previous.params().equals(params) ? previous : new Stored(params, 400, error("idempotency_error",
                        null, "Keys for idempotent requests can only be used with the same parameters they were first used with."));
            }
            if (key != null && !inFlight.add(key)) {
                return null;
            }
        }
        sleep(processingMillis);
        synchronized (this) {
            String id = "pi_test_" + String.format("%019d", sequence.incrementAndGet());
            statusById.put(id, "requires_payment_method");
            Stored created = new Stored(params, 200, paymentIntent(id, params));
            if (key != null) {
                byIdempotencyKey.put(key, created);
                inFlight.remove(key);
            }
            return created;
        }
    }

    // Retarde une réponse simulée pendant la durée demandée.
    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Présente l'état courant du paiement conservé par le test.
    private void retrieve(HttpExchange exchange, String id) throws IOException {
        if (!statusById.containsKey(id)) {
            respond(exchange, 404, error("invalid_request_error", "resource_missing", "No such payment_intent."));
            return;
        }
        respond(exchange, 200, paymentIntent(id, Map.of()));
    }

    // Annule le paiement simulé et déclenche l'action préparée.
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
        Runnable action = cancellationAction;
        cancellationAction = null;
        if (action != null) {
            action.run();
        }
        awaitBarrier(cancellationBarrier);
        respond(exchange, 200, paymentIntent(id, Map.of()));
    }

    // Attend les appels concurrents avec une durée bornée.
    private void awaitBarrier() {
        awaitBarrier(creationBarrier);
    }

    // Attend les appels concurrents avec une durée bornée.
    private void awaitBarrier(CyclicBarrier barrier) {
        if (barrier == null) {
            return;
        }
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Les créations concurrentes attendues ne sont pas arrivées", e);
        }
    }

    // Compose la réponse d'un paiement Stripe simulé.
    private String paymentIntent(String id, Map<String, String> params) {
        return "{\"id\":\"" + id + "\",\"object\":\"payment_intent\",\"status\":\"" + statusById.get(id) + "\","
                + "\"client_secret\":\"" + id + "_secret_test\","
                + "\"amount\":" + params.getOrDefault("amount", "0") + ","
                + "\"currency\":\"" + params.getOrDefault("currency", "eur") + "\","
                + "\"metadata\":{}}";
    }

    // Compose la réponse d'une erreur Stripe simulée.
    private static String error(String type, String code, String message) {
        return "{\"error\":{\"type\":\"" + type + "\"," + (code != null ? "\"code\":\"" + code + "\"," : "")
                + "\"message\":\"" + message + "\"}}";
    }

    // Lit les paramètres d'un appel au serveur simulé.
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

    // Envoie la réponse HTTP préparée par le test.
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
