package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.dto.out.SellerEarningsResponse;
import com.minoh.lumiris_backend.dto.out.SellerSaleResponse;
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
import java.util.Set;
import java.util.UUID;

// LUMIRIS-22 · Agrégats du tableau de bord vendeur (ATELIER) : ventes, CA net, garde-robe, vues.
@Service
@RequiredArgsConstructor
public class SellerStatsService {

    // Une commande "vendue" = encaissée et non remboursée, à tout stade du cycle de vie.
    private static final Set<OrderStatus> SOLD = OrderStatus.sold();

    private final MarketplaceOrderRepository orderRepository;
    private final MarketplaceProductRepository productRepository;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;

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

    // Historique des ventes de l'atelier (hors commandes non confirmées).
    @Transactional(readOnly = true)
    public List<SellerSaleResponse> getSales(String userEmail) {
        User artisan = requireArtisan(userEmail);
        return orderRepository.findBySeller_IdOrderByCreatedAtDesc(artisan.getId()).stream()
                .filter(o -> o.getStatus() != OrderStatus.PENDING)
                .map(SellerSaleResponse::from)
                .toList();
    }

    // Trésorerie escrow : montants nets retenus (en attente d'expédition) vs déjà reversés.
    @Transactional(readOnly = true)
    public SellerEarningsResponse getEarnings(String userEmail) {
        User artisan = requireArtisan(userEmail);
        return new SellerEarningsResponse(
                orderRepository.heldNetCentsBySeller(artisan.getId(), SOLD),
                orderRepository.releasedNetCentsBySeller(artisan.getId()),
                "EUR"
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
