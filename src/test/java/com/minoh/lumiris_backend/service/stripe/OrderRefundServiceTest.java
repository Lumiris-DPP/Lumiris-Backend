package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.marketplace.order.service.OrderRefundService;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.stripe.Stripe;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrderRefundServiceTest {

    @Test
    void equalPartialAmounts_createTwoDistinctRefunds() throws Exception {
        Map<String, String> refunds = new HashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/refunds", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            String id = refunds.computeIfAbsent(key, ignored -> "re_" + refunds.size());
            byte[] body = ("{\"id\":\"" + id + "\",\"object\":\"refund\",\"amount\":200}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String previousApiKey = Stripe.apiKey;
        Stripe.overrideApiBase("http://127.0.0.1:" + server.getAddress().getPort());
        Stripe.apiKey = "sk_test_dummy";
        try {
            StripeProperties properties = new StripeProperties("sk_test_dummy", null, null, false, null, null);
            OrderRefundService service = new OrderRefundService(properties);
            MarketplaceOrder order = new MarketplaceOrder();
            order.setId(UUID.randomUUID());
            order.setStripePaymentIntentId("pi_test");
            order.setAmountTotalCents(1000);
            var first = service.refund(order, 200, "Geste");
            order.setRefundedCents(200);
            var second = service.refund(order, 200, "Geste");
            assertThat(second.refundId()).isNotEqualTo(first.refundId());
            assertThat(refunds).hasSize(2);
        } finally {
            server.stop(0);
            Stripe.overrideApiBase(Stripe.LIVE_API_BASE);
            Stripe.apiKey = previousApiKey;
        }
    }
}
