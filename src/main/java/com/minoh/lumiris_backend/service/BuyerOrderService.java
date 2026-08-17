package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.OrderDetailResponse;
import com.minoh.lumiris_backend.dto.out.OrderGroupResponse;
import com.minoh.lumiris_backend.dto.out.OrderResponse;
import com.minoh.lumiris_backend.dto.out.WardrobeItemResponse;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.OrderEventRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.repository.WardrobeItemRepository;
import com.minoh.lumiris_backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Lectures côté acheteur (VISION) : historique, suivi d'une commande, garde-robe. Toute commande
// non possédée renvoie 404 plutôt que 403 — un identifiant de commande ne doit rien révéler.
@Service
@RequiredArgsConstructor
public class BuyerOrderService {

    private final MarketplaceOrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final StorageService storageService;
    private final WardrobeItemRepository wardrobeItemRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return orderRepository.findByBuyer_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(OrderResponse::from).toList();
    }

    // Suivi complet d'une commande : la ligne, son adresse et la timeline de ses transitions.
    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrder(String userEmail, UUID orderId) {
        MarketplaceOrder order = requireOwned(userEmail, orderId);
        return OrderDetailResponse.from(order, eventRepository.findByOrder_IdOrderByCreatedAtAsc(orderId),
                storageService::getPresignedUrl);
    }

    // Groupe de commande (écran de confirmation) = toutes les lignes d'un PaymentIntent de l'acheteur.
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

    @Transactional(readOnly = true)
    public List<WardrobeItemResponse> getWardrobe(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        return wardrobeItemRepository.findOwnedWithPassport(user.getId())
                .stream().map(WardrobeItemResponse::from).toList();
    }

    private MarketplaceOrder requireOwned(String userEmail, UUID orderId) {
        User user = userRepository.getByEmail(userEmail);
        return orderRepository.findById(orderId)
                .filter(o -> o.getBuyer() != null && o.getBuyer().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
    }
}
