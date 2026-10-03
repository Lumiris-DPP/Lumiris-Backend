package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderEvent;
import com.minoh.lumiris_backend.entity.OrderStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.OrderEventRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Expose les commandes appartenant à l’atelier. */
@Service
@RequiredArgsConstructor
public class SellerOrderService {

    private final MarketplaceOrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;

    /** Liste les commandes visibles par l’atelier connecté. */
    @Transactional(readOnly = true)
    public List<SellerOrderResponse> list(String sellerEmail) {
        User seller = requireArtisan(sellerEmail);
        List<MarketplaceOrder> orders = orderRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId()).stream()
                .filter(o -> o.getStatus() != OrderStatus.PENDING)
                .toList();
        Map<UUID, List<OrderEvent>> timelines = loadTimelines(orders);
        return orders.stream()
                .map(o -> SellerOrderResponse.from(o, timelines.getOrDefault(o.getId(), List.of()),
                        storageService::getPresignedUrl))
                .toList();
    }

    /** Charge une commande appartenant à l’atelier. */
    @Transactional(readOnly = true)
    public SellerOrderResponse get(String sellerEmail, UUID orderId) {
        User seller = requireArtisan(sellerEmail);
        MarketplaceOrder order = orderRepository.findById(orderId)
                .filter(o -> o.getSeller() != null && o.getSeller().getId().equals(seller.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        return SellerOrderResponse.from(order, eventRepository.findByOrder_IdOrderByCreatedAtAsc(orderId),
                storageService::getPresignedUrl);
    }

    private Map<UUID, List<OrderEvent>> loadTimelines(List<MarketplaceOrder> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = orders.stream().map(MarketplaceOrder::getId).toList();
        return eventRepository.findByOrder_IdInOrderByCreatedAtAsc(ids).stream()
                .collect(Collectors.groupingBy(e -> e.getOrder().getId()));
    }

    /** Vérifie le rôle artisan du vendeur. */
    private User requireArtisan(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ARTISAN) {
            throw new RoleNotAllowedException("Seuls les artisans disposent d'un tableau de bord vendeur.");
        }
        return user;
    }
}
