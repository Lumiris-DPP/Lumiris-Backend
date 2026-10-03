package com.minoh.lumiris_backend.marketplace.catalog.repository;

import com.minoh.lumiris_backend.entity.MarketplaceSizeMeasurement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accède au guide des mesures de chaque annonce. */
public interface MarketplaceSizeMeasurementRepository extends JpaRepository<MarketplaceSizeMeasurement, UUID> {

    /** Liste les mesures d’une annonce dans l’ordre d’affichage. */
    List<MarketplaceSizeMeasurement> findByProduct_IdOrderByPositionAscLabelAsc(UUID productId);

    /** Charge en lot les mesures dans l’ordre d’affichage. */
    List<MarketplaceSizeMeasurement> findByProduct_IdInOrderByPositionAscLabelAsc(Collection<UUID> productIds);

    // Suppression en masse et non dérivée : le guide est remplacé en bloc, et Hibernate ordonne ses
    // insertions AVANT ses suppressions au flush — réenregistrer la même mesure violerait alors
    // l'index unique. Une suppression en masse part immédiatement.
    @Modifying
    @Query("delete from MarketplaceSizeMeasurement m where m.product.id = :productId")
    int deleteByProductId(@Param("productId") UUID productId);
}
