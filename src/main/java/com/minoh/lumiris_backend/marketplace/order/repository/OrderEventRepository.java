package com.minoh.lumiris_backend.marketplace.order.repository;

import com.minoh.lumiris_backend.entity.OrderEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accède aux journaux chronologiques des commandes. */
public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    /** Lit le journal chronologique d’une commande. */
    List<OrderEvent> findByOrder_IdOrderByCreatedAtAsc(UUID orderId);

    /** Charge en lot les journaux chronologiques des commandes. */
    List<OrderEvent> findByOrder_IdInOrderByCreatedAtAsc(List<UUID> orderIds);
}
