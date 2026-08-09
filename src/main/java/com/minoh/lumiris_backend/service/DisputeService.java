package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.repository.OrderEventRepository;
import com.minoh.lumiris_backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        return orders.stream()
                .map(o -> SellerOrderResponse.from(o, eventRepository.findByOrder_IdOrderByCreatedAtAsc(o.getId()),
                        storageService::getPresignedUrl))
                .toList();
    }
}
