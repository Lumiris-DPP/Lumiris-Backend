package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.PayoutExpectation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutScheduleResolverTest {

    private static final Instant SHIP_DUE_AT = Instant.parse("2026-08-05T10:00:00Z");
    private static final Instant SHIPPED_AT = Instant.parse("2026-08-04T10:00:00Z");

    private PayoutScheduleResolver resolver;
    private Duration autoDeliverDelay;

    @BeforeEach
    void setUp() {
        MarketplaceProperties properties = new MarketplaceProperties();
        resolver = new PayoutScheduleResolver(properties);
        autoDeliverDelay = properties.autoDeliverDelay();
    }

    @Test
    @DisplayName("Une commande payée non expédiée est attendue au délai de livraison suivant la date promise")
    void paidOrderIsScheduledFromShipDueDate() {
        MarketplaceOrder order = order(OrderStatus.PAID);
        order.setShipDueAt(SHIP_DUE_AT);

        PayoutScheduleResolver.PayoutForecast forecast = resolver.forecast(order);

        assertThat(forecast.expectation()).isEqualTo(PayoutExpectation.SCHEDULED);
        assertThat(forecast.expectedAt()).isEqualTo(SHIP_DUE_AT.plus(autoDeliverDelay));
    }

    @Test
    @DisplayName("Une commande expédiée est attendue au délai de livraison suivant l'expédition réelle")
    void shippedOrderIsScheduledFromShippedAt() {
        MarketplaceOrder order = order(OrderStatus.SHIPPED);
        order.setShipDueAt(SHIP_DUE_AT);
        order.setShippedAt(SHIPPED_AT);

        PayoutScheduleResolver.PayoutForecast forecast = resolver.forecast(order);

        assertThat(forecast.expectation()).isEqualTo(PayoutExpectation.SCHEDULED);
        assertThat(forecast.expectedAt()).isEqualTo(SHIPPED_AT.plus(autoDeliverDelay));
    }

    @Test
    @DisplayName("Une commande livrée sans transfert est imminente, sans date promise")
    void deliveredOrderIsImminentWithoutDate() {
        assertThat(resolver.forecast(order(OrderStatus.DELIVERED)))
                .isEqualTo(new PayoutScheduleResolver.PayoutForecast(PayoutExpectation.IMMINENT, null));
        assertThat(resolver.forecast(order(OrderStatus.COMPLETED)))
                .isEqualTo(new PayoutScheduleResolver.PayoutForecast(PayoutExpectation.IMMINENT, null));
    }

    @Test
    @DisplayName("Un litige ouvert suspend le versement, quel que soit l'avancement de la commande")
    void openDisputeSuspendsEveryStatus() {
        for (OrderStatus status : OrderStatus.values()) {
            MarketplaceOrder order = order(status);
            order.setShipDueAt(SHIP_DUE_AT);
            order.setShippedAt(SHIPPED_AT);
            order.setDisputeStatus(DisputeStatus.OPEN);

            assertThat(resolver.forecast(order))
                    .as("statut %s", status)
                    .isEqualTo(new PayoutScheduleResolver.PayoutForecast(PayoutExpectation.ON_HOLD, null));
        }
    }

    @Test
    @DisplayName("Toute branche retour suspend le versement sans date")
    void returnBranchIsOnHold() {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.RETURN_REQUESTED, OrderStatus.RETURN_APPROVED,
                OrderStatus.RETURN_REFUSED, OrderStatus.RETURN_RECEIVED}) {
            assertThat(resolver.forecast(order(status)))
                    .as("statut %s", status)
                    .isEqualTo(new PayoutScheduleResolver.PayoutForecast(PayoutExpectation.ON_HOLD, null));
        }
    }

    @Test
    @DisplayName("Une commande d'avant la migration, sans date promise, retombe sur sa date d'achat")
    void legacyOrderFallsBackToCreatedAt() {
        MarketplaceOrder order = order(OrderStatus.PAID);

        PayoutScheduleResolver.PayoutForecast forecast = resolver.forecast(order);

        assertThat(forecast.expectation()).isEqualTo(PayoutExpectation.SCHEDULED);
        assertThat(forecast.expectedAt()).isNotNull();
    }

    private static MarketplaceOrder order(OrderStatus status) {
        MarketplaceOrder order = new MarketplaceOrder() {
            @Override
            public Instant getCreatedAt() {
                return Instant.parse("2026-08-01T10:00:00Z");
            }
        };
        order.setStatus(status);
        order.setDisputeStatus(DisputeStatus.NONE);
        return order;
    }
}
