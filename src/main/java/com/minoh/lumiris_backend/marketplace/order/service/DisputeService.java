package com.minoh.lumiris_backend.marketplace.order.service;

import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderEvent;
import com.minoh.lumiris_backend.marketplace.order.dto.out.SellerOrderResponse;
import com.minoh.lumiris_backend.marketplace.order.repository.MarketplaceOrderRepository;
import com.minoh.lumiris_backend.marketplace.order.repository.OrderEventRepository;
import com.minoh.lumiris_backend.service.StorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
