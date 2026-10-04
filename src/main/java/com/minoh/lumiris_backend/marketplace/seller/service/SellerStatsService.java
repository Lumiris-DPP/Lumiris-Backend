package com.minoh.lumiris_backend.marketplace.seller.service;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.PayoutExpectation;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.marketplace.catalog.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerEarningsResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerPayoutEntryResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerPayoutScheduleResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerSaleResponse;
import com.minoh.lumiris_backend.marketplace.seller.dto.out.SellerStatsResponse;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerStatsService {

    private static final Set<OrderStatus> SOLD = OrderStatus.sold();

    private final MarketplaceOrderRepository orderRepository;
    private final MarketplaceProductRepository productRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;
    private final PayoutScheduleResolver payoutScheduleResolver;

    @Transactional(readOnly = true)
    public SellerStatsResponse getStats(String userEmail) {
        User artisan = requireArtisan(userEmail);
        UUID sellerId = artisan.getId();
        UUID artisanProfileId = artisan.getArtisanProfile().getId();

        long gross = orderRepository.grossCentsBySeller(sellerId, SOLD);
        long commission = orderRepository.commissionCentsBySeller(sellerId, SOLD);
        return new SellerStatsResponse(
                orderRepository.countBySeller_IdAndStatusIn(sellerId, SOLD),
                gross,
                commission,
                gross - commission,
                wardrobeItemRepository.countByOrder_Seller_Id(sellerId),
                productRepository.totalViewsByArtisanProfile(artisanProfileId),
                productRepository.countByArtisanProfileId(artisanProfileId),
                productRepository.countByArtisanProfileIdAndStatus(
                        artisanProfileId, com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED)
        );
    }

    @Transactional(readOnly = true)
    public List<SellerSaleResponse> getSales(String userEmail) {
        User artisan = requireArtisan(userEmail);
        return orderRepository.findBySeller_IdOrderByCreatedAtDesc(artisan.getId()).stream()
                .filter(o -> o.getStatus() != OrderStatus.PENDING)
                .map(SellerSaleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerEarningsResponse getEarnings(String userEmail) {
        User artisan = requireArtisan(userEmail);
        return new SellerEarningsResponse(
                orderRepository.heldNetCentsBySeller(artisan.getId(), SOLD),
                orderRepository.releasedNetCentsBySeller(artisan.getId()),
                "EUR"
        );
    }

    @Transactional(readOnly = true)
    public SellerPayoutScheduleResponse getPayoutSchedule(String userEmail) {
        User artisan = requireArtisan(userEmail);
        List<SellerPayoutEntryResponse> entries = orderRepository
                .findUnreleasedBySeller(artisan.getId(), SOLD).stream()
                .map(this::toPayoutEntry)
                .sorted(Comparator.comparing(SellerPayoutEntryResponse::expectedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        long scheduled = 0;
        long onHold = 0;
        for (SellerPayoutEntryResponse entry : entries) {
            if (entry.expectation() == PayoutExpectation.SCHEDULED) {
                scheduled += entry.netCents();
            } else if (entry.expectation() == PayoutExpectation.ON_HOLD
                    || entry.expectation() == PayoutExpectation.IMMINENT) {
                onHold += entry.netCents();
            }
        }
        return new SellerPayoutScheduleResponse(
                scheduled,
                orderRepository.releasedNetCentsBySeller(artisan.getId()),
                onHold,
                "EUR",
                entries
        );
    }

    private SellerPayoutEntryResponse toPayoutEntry(MarketplaceOrder order) {
        PayoutScheduleResolver.PayoutForecast forecast = payoutScheduleResolver.forecast(order);
        return new SellerPayoutEntryResponse(
                order.getId(),
                order.getProduct() != null ? order.getProduct().getName() : null,
                order.getVariantLabel(),
                order.getBuyer() != null ? order.getBuyer().getName() : null,
                order.getNetCents(),
                order.getCurrency(),
                forecast.expectedAt(),
                forecast.expectation(),
                order.getStatus()
        );
    }

    private User requireArtisan(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        if (user.getRole() != UserRole.ARTISAN) {
            throw new RoleNotAllowedException("Seuls les artisans disposent d'un tableau de bord vendeur.");
        }
        return user;
    }
}
