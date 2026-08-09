package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    List<OrderEvent> findByOrder_IdOrderByCreatedAtAsc(UUID orderId);

    List<OrderEvent> findByOrder_IdInOrderByCreatedAtAsc(List<UUID> orderIds);
}
