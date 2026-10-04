package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.MarketplaceProperties;
import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerPayoutScheduleResponse;
import com.minoh.lumiris_backend.marketplace.seller.service.PayoutScheduleResolver;
import com.minoh.lumiris_backend.marketplace.seller.service.SellerStatsService;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Vérifie les montants de versement et l'accès vendeur.
class SellerStatsServiceTest {
    private final MarketplaceOrderRepository orders = mock(MarketplaceOrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SellerStatsService service = new SellerStatsService(orders,
            mock(MarketplaceProductRepository.class), mock(WardrobeItemRepository.class), users,
            new PayoutScheduleResolver(new MarketplaceProperties()));

    // Vérifie les totaux vendeur et l'ordre des versements attendus.
    @Test
    void scheduleSeparatesExpectedHeldAndImminentAmountsWithoutChangingEntries() {
        User seller = new User();
        seller.setId(UUID.randomUUID());
        seller.setRole(UserRole.ARTISAN);
        when(users.getByEmail("atelier@test")).thenReturn(seller);
        MarketplaceOrder late = order(OrderStatus.PAID, 300);
        late.setShipDueAt(Instant.parse("2026-10-04T12:00:00Z"));
        MarketplaceOrder early = order(OrderStatus.SHIPPED, 500);
        early.setShippedAt(Instant.parse("2026-10-03T12:00:00Z"));
        MarketplaceOrder disputed = order(OrderStatus.PAID, 700);
        disputed.setDisputeStatus(DisputeStatus.OPEN);
        MarketplaceOrder imminent = order(OrderStatus.DELIVERED, 1100);
        MarketplaceOrder returned = order(OrderStatus.RETURN_APPROVED, 1300);
        when(orders.findUnreleasedBySeller(seller.getId(), OrderStatus.sold()))
                .thenReturn(List.of(late, early, disputed, imminent, returned));
        when(orders.releasedNetCentsBySeller(seller.getId())).thenReturn(1700L);

        SellerPayoutScheduleResponse response = service.getPayoutSchedule("atelier@test");

        assertThat(response.scheduledCents()).isEqualTo(800);
        assertThat(response.onHoldCents()).isEqualTo(3100);
        assertThat(response.releasedCents()).isEqualTo(1700);
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.entries()).extracting(entry -> entry.orderId())
                .containsExactly(early.getId(), late.getId(), disputed.getId(), imminent.getId(), returned.getId());
        assertThat(response.entries()).extracting(entry -> entry.netCents())
                .containsExactly(500, 300, 700, 1100, 1300);
    }

    // Vérifie le refus d'accès vendeur pour un compte acheteur.
    @Test
    void consumerCannotReadSellerSchedule() {
        User consumer = new User();
        consumer.setRole(UserRole.CONSUMER);
        when(users.getByEmail("acheteur@test")).thenReturn(consumer);

        assertThatThrownBy(() -> service.getPayoutSchedule("acheteur@test"))
                .isInstanceOf(RoleNotAllowedException.class);
        verifyNoInteractions(orders);
    }

    // Prépare une commande avec le montant vendeur attendu.
    private MarketplaceOrder order(OrderStatus status, int netCents) {
        MarketplaceOrder order = new MarketplaceOrder();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        order.setNetCents(netCents);
        order.setCurrency("EUR");
        return order;
    }
}
