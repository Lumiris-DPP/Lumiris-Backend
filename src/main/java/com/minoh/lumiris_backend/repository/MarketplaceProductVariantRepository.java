package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.MarketplaceProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MarketplaceProductVariantRepository extends JpaRepository<MarketplaceProductVariant, UUID> {

    List<MarketplaceProductVariant> findByProduct_IdOrderByPositionAscIdAsc(UUID productId);

    List<MarketplaceProductVariant> findByProduct_IdInOrderByPositionAscIdAsc(Collection<UUID> productIds);

    // Réservation de stock au checkout — décrément conditionnel atomique : ne passe que si le stock
    // disponible couvre la quantité (garde anti-survente). Renvoie le nb de lignes affectées
    // (1 = réservé, 0 = stock insuffisant, à traiter en 422 côté service).
    // La version est incrémentée explicitement pour qu'une sauvegarde artisan partie d'un stock
    // périmé échoue en conflit au lieu d'écraser la vente.
    @Modifying
    @Query("update MarketplaceProductVariant v set v.stock = v.stock - :qty, v.version = v.version + 1 "
            + "where v.id = :id and v.stock >= :qty")
    int decrementStock(@Param("id") UUID id, @Param("qty") int qty);

    // Remise en stock après remboursement : la pièce n'a jamais changé de propriétaire durablement.
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
