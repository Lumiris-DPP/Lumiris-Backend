package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, UUID> {

    Optional<MarketplaceOrder> findByStripeCheckoutSessionId(String sessionId);

    // Fulfillment du paiement embarqué : toutes les lignes de commande d'un même PaymentIntent.
    List<MarketplaceOrder> findByStripePaymentIntentId(String paymentIntentId);

    List<MarketplaceOrder> findByBuyer_IdOrderByCreatedAtDesc(UUID buyerId);

    List<MarketplaceOrder> findBySeller_IdOrderByCreatedAtDesc(UUID sellerId);

    // ── Statistiques vendeur (ventes réglées) ───────────────────────────────
    long countBySeller_IdAndStatusIn(UUID sellerId, Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.amountTotalCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses")
    long grossCentsBySeller(@Param("sellerId") UUID sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.commissionCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses")
    long commissionCentsBySeller(@Param("sellerId") UUID sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    // Ventes réglées par produit (pour la colonne "Ventes" du catalogue).
    @Query("select o.product.id, count(o) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses group by o.product.id")
    List<Object[]> salesCountByProduct(@Param("sellerId") UUID sellerId,
                                       @Param("statuses") Collection<OrderStatus> statuses);
}
