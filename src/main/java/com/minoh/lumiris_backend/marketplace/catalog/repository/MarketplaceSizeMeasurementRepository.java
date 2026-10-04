package com.minoh.lumiris_backend.marketplace.catalog.repository;

import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceSizeMeasurementRepository extends JpaRepository<MarketplaceSizeMeasurement, UUID> {

    List<MarketplaceSizeMeasurement> findByProduct_IdOrderByPositionAscLabelAsc(UUID productId);

    List<MarketplaceSizeMeasurement> findByProduct_IdInOrderByPositionAscLabelAsc(Collection<UUID> productIds);

    @Modifying
    @Query("delete from MarketplaceSizeMeasurement m where m.product.id = :productId")
    int deleteByProductId(@Param("productId") UUID productId);
}
