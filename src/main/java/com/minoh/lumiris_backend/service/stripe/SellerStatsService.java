package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.dto.out.SellerStatsResponse;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.MarketplaceProductRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// LUMIRIS-22 · Agrégats du tableau de bord vendeur (ATELIER) : ventes, CA net, garde-robe, vues.
@Service
@RequiredArgsConstructor
public class SellerStatsService {

    // Une commande "vendue" = payée puis (éventuellement) honorée.
    private static final List<OrderStatus> SOLD = List.of(OrderStatus.PAID, OrderStatus.FULFILLED);

    private final MarketplaceOrderRepository orderRepository;
    private final MarketplaceProductRepository productRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SellerStatsResponse getStats(String userEmail) {
        User artisan = userRepository.getByEmail(userEmail);
        if (artisan.getRole() != UserRole.ARTISAN) {
            throw new RoleNotAllowedException("Seuls les artisans disposent d'un tableau de bord vendeur.");
        }
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
}
