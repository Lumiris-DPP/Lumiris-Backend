package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MarketplaceSizeMeasurementRepository extends JpaRepository<MarketplaceSizeMeasurement, UUID> {

    List<MarketplaceSizeMeasurement> findByProduct_IdOrderByPositionAscLabelAsc(UUID productId);

    List<MarketplaceSizeMeasurement> findByProduct_IdInOrderByPositionAscLabelAsc(Collection<UUID> productIds);

    // Suppression en masse et non dérivée : le guide est remplacé en bloc, et Hibernate ordonne ses
    // insertions AVANT ses suppressions au flush — réenregistrer la même mesure violerait alors
    // l'index unique. Une suppression en masse part immédiatement.
    @Modifying
    @Query("delete from MarketplaceSizeMeasurement m where m.product.id = :productId")
    int deleteByProductId(@Param("productId") UUID productId);
}
