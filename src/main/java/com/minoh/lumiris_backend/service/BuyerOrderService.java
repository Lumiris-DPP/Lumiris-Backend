package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.dto.out.OrderDetailResponse;
import com.minoh.lumiris_backend.dto.out.OrderGroupResponse;
import com.minoh.lumiris_backend.dto.out.OrderResponse;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.OrderEventRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import com.minoh.lumiris_backend.service.StorageService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Consulte les commandes appartenant à l'acheteur connecté.
@Service
@RequiredArgsConstructor
public class BuyerOrderService {

    private final MarketplaceOrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final StorageService storageService;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;

    // Liste les commandes appartenant à l'acheteur connecté.
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return orderRepository.findByBuyer_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(OrderResponse::from).toList();
    }

    // Retrouve une commande appartenant à l'acheteur connecté.
    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrder(String userEmail, UUID orderId) {
        MarketplaceOrder order = requireOwned(userEmail, orderId);
        return OrderDetailResponse.from(order, eventRepository.findByOrder_IdOrderByCreatedAtAsc(orderId),
                storageService::getPresignedUrl);
    }

    // Regroupe les commandes de l'acheteur issues du paiement.
    @Transactional(readOnly = true)
    public OrderGroupResponse getMyOrderGroup(String userEmail, String paymentIntentId) {
        User user = userRepository.getByEmail(userEmail);
        List<MarketplaceOrder> orders = orderRepository.findByStripePaymentIntentId(paymentIntentId).stream()
                .filter(o -> o.getBuyer() != null && o.getBuyer().getId().equals(user.getId()))
                .toList();
        if (orders.isEmpty()) {
            throw new ResourceNotFoundException("Commande introuvable");
        }
        return OrderGroupResponse.from(paymentIntentId, orders);
    }

    // Liste les pièces acquises par l'utilisateur connecté.
    @Transactional(readOnly = true)
    public List<WardrobeItemResponse> getWardrobe(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return wardrobeItemRepository.findOwnedWithPassport(user.getId())
                .stream().map(WardrobeItemResponse::from).toList();
    }

    // Vérifie que la commande appartient à l'acheteur connecté.
    private MarketplaceOrder requireOwned(String userEmail, UUID orderId) {
        User user = userRepository.getByEmail(userEmail);
        return orderRepository.findById(orderId)
                .filter(o -> o.getBuyer() != null && o.getBuyer().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
    }
}
