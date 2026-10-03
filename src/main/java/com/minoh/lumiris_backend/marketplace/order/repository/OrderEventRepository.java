package com.minoh.lumiris_backend.marketplace.order.repository;

import com.minoh.lumiris_backend.entity.OrderEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    List<OrderEvent> findByOrder_IdOrderByCreatedAtAsc(UUID orderId);

    List<OrderEvent> findByOrder_IdInOrderByCreatedAtAsc(List<UUID> orderIds);
}
