package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderEvent;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.OrderEventRepository;
import com.minoh.lumiris_backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// File d'arbitrage de la plateforme. La vue reprend le DTO vendeur : l'arbitre a besoin
// exactement des mêmes éléments (montants, adresse, suivi, historique) pour trancher.
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final MarketplaceOrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<SellerOrderResponse> listOpen() {
        List<MarketplaceOrder> orders = orderRepository.findByDisputeStatusOrderByDisputeOpenedAtAsc(DisputeStatus.OPEN);
        Map<UUID, List<OrderEvent>> timelines = eventRepository
                .findByOrder_IdInOrderByCreatedAtAsc(orders.stream().map(MarketplaceOrder::getId).toList()).stream()
                .collect(Collectors.groupingBy(event -> event.getOrder().getId()));

        return orders.stream()
                .map(o -> SellerOrderResponse.from(o, timelines.getOrDefault(o.getId(), List.of()),
                        storageService::getPresignedUrl))
                .toList();
    }
}
