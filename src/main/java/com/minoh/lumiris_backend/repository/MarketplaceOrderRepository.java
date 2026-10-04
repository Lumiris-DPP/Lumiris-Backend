package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DisputeStatus;
import com.minoh.lumiris_backend.entity.MarketplaceOrder;
import com.minoh.lumiris_backend.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, UUID> {

    Optional<MarketplaceOrder> findByStripeCheckoutSessionId(String sessionId);

    boolean existsByProduct_Id(UUID productId);

    List<MarketplaceOrder> findByStripePaymentIntentId(String paymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketplaceOrder o where o.stripePaymentIntentId = :paymentIntentId order by o.id")
    List<MarketplaceOrder> lockByStripePaymentIntentId(@Param("paymentIntentId") String paymentIntentId);

    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:paymentIntentId))) as verrou",
            nativeQuery = true)
    int lockPaymentIntent(@Param("paymentIntentId") String paymentIntentId);

    @Query("""
            select o.variant.id, sum(o.quantity) from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.buyer.id = :buyerId
              and o.variant.id in :variantIds
            group by o.variant.id
            """)
    List<Object[]> pendingQuantityByVariant(@Param("buyerId") UUID buyerId,
                                            @Param("variantIds") Collection<UUID> variantIds);

    Optional<MarketplaceOrder> findByCarrierParcelId(String carrierParcelId);

    @EntityGraph(attributePaths = {"product", "seller", "seller.artisanProfile"})
    List<MarketplaceOrder> findByBuyer_IdOrderByCreatedAtDesc(UUID buyerId);

    @EntityGraph(attributePaths = {"product", "buyer", "buyer.artisanProfile", "shippingLabelFile"})
    List<MarketplaceOrder> findBySeller_IdOrderByCreatedAtDesc(UUID sellerId);

    @EntityGraph(attributePaths = {"product", "buyer", "buyer.artisanProfile", "shippingLabelFile"})
    List<MarketplaceOrder> findByDisputeStatusOrderByDisputeOpenedAtAsc(DisputeStatus disputeStatus);

    long countBySeller_IdAndStatusIn(UUID sellerId, Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.amountTotalCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses")
    long grossCentsBySeller(@Param("sellerId") UUID sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.commissionCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses")
    long commissionCentsBySeller(@Param("sellerId") UUID sellerId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select o.product.id, count(o) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses group by o.product.id")
    List<Object[]> salesCountByProduct(@Param("sellerId") UUID sellerId,
                                       @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.netCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.status in :statuses and o.stripeTransferId is null")
    long heldNetCentsBySeller(@Param("sellerId") UUID sellerId,
                              @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.netCents), 0) from MarketplaceOrder o "
            + "where o.seller.id = :sellerId and o.stripeTransferId is not null")
    long releasedNetCentsBySeller(@Param("sellerId") UUID sellerId);

    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.SHIPPED
              and o.shippedAt < :threshold
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
            """)
    List<MarketplaceOrder> findStaleShipped(@Param("threshold") Instant threshold);

    @Query("""
            select distinct o.stripePaymentIntentId from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.stripePaymentIntentId is not null
              and o.createdAt < :threshold
            """)
    List<String> findAbandonedPendingPaymentIntents(@Param("threshold") Instant threshold);

    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.stripePaymentIntentId is null
              and o.createdAt < :threshold
            """)
    List<MarketplaceOrder> findAbandonedPendingWithoutPaymentIntent(@Param("threshold") Instant threshold);

    @Query("""
            select distinct o.stripePaymentIntentId from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PENDING
              and o.buyer.id = :buyerId
              and o.stripePaymentIntentId <> :currentPaymentIntentId
            """)
    List<String> findSupersededPendingPaymentIntents(@Param("buyerId") UUID buyerId,
                                                     @Param("currentPaymentIntentId") String currentPaymentIntentId);

    @Query("""
            select o from MarketplaceOrder o
            where o.status in (com.minoh.lumiris_backend.entity.OrderStatus.RETURN_REFUSED,
                               com.minoh.lumiris_backend.entity.OrderStatus.RETURN_RECEIVED)
              and o.returnDecidedAt < :threshold
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
            """)
    List<MarketplaceOrder> findStaleReturns(@Param("threshold") Instant threshold);

    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.PAID
              and o.shipReminderSentAt is null
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
              and o.shipDueAt < :threshold
            """)
    List<MarketplaceOrder> findOverdueUnshipped(@Param("threshold") Instant threshold);

    @Query("""
            select o from MarketplaceOrder o
            where o.seller.id = :sellerId
              and o.status in :statuses
              and o.stripeTransferId is null
            """)
    List<MarketplaceOrder> findUnreleasedBySeller(@Param("sellerId") UUID sellerId,
                                                  @Param("statuses") Collection<OrderStatus> statuses);

    @Query("""
            select o from MarketplaceOrder o
            where o.status in (com.minoh.lumiris_backend.entity.OrderStatus.DELIVERED,
                               com.minoh.lumiris_backend.entity.OrderStatus.COMPLETED)
              and o.stripeTransferId is null
              and o.netCents > 0
            """)
    List<MarketplaceOrder> findUnreleased();

    @Query("""
            select o from MarketplaceOrder o
            where o.status = com.minoh.lumiris_backend.entity.OrderStatus.DELIVERED
              and o.deliveredAt < :threshold
              and o.disputeStatus <> com.minoh.lumiris_backend.entity.DisputeStatus.OPEN
            """)
    List<MarketplaceOrder> findStaleDelivered(@Param("threshold") Instant threshold);
}
