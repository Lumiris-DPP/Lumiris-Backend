package com.minoh.lumiris_backend.marketplace.catalog.repository;

import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceProductVariantRepository extends JpaRepository<MarketplaceProductVariant, UUID> {

    List<MarketplaceProductVariant> findByProduct_IdOrderByPositionAscIdAsc(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from MarketplaceProductVariant v where v.product.id = :productId order by v.id")
    List<MarketplaceProductVariant> lockByProductId(@Param("productId") UUID productId);

    List<MarketplaceProductVariant> findByProduct_IdInOrderByPositionAscIdAsc(Collection<UUID> productIds);

    @Modifying
    @Query("update MarketplaceProductVariant v set v.stock = v.stock - :qty, v.version = v.version + 1 "
            + "where v.id = :id and v.stock >= :qty")
    int decrementStock(@Param("id") UUID id, @Param("qty") int qty);

    @Modifying
    @Query("update MarketplaceProductVariant v set v.stock = v.stock + :qty, v.version = v.version + 1 "
            + "where v.id = :id")
    int incrementStock(@Param("id") UUID id, @Param("qty") int qty);

    @Query("""
            select v from MarketplaceProductVariant v
            join fetch v.product p
            join fetch p.artisanProfile
            where v.id in :ids
            """)
    List<MarketplaceProductVariant> findAllForCheckout(@Param("ids") Collection<UUID> ids);
}
