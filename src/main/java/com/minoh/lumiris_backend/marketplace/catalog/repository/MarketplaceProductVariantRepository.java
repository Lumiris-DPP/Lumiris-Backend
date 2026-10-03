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

    // Déclinaisons d'une annonce lues par l'atelier pour les réécrire, verrouillées jusqu'à la fin de
    // sa transaction : une vente ou une remise en stock arrivée pendant l'enregistrement attend au lieu
    // d'être écrasée par lui. Ordre fixe (identifiant croissant), celui des réservations du checkout,
    // pour que deux transactions qui se croisent s'attendent sans s'interbloquer.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from MarketplaceProductVariant v where v.product.id = :productId order by v.id")
    List<MarketplaceProductVariant> lockByProductId(@Param("productId") UUID productId);

    List<MarketplaceProductVariant> findByProduct_IdInOrderByPositionAscIdAsc(Collection<UUID> productIds);

    // Réservation de stock au checkout — décrément conditionnel atomique : ne passe que si le stock
    // disponible couvre la quantité (garde anti-survente). Renvoie le nb de lignes affectées
    // (1 = réservé, 0 = stock insuffisant, à traiter en 422 côté service).
    // La version est incrémentée explicitement pour qu'une sauvegarde artisan partie d'un stock
    // périmé échoue en conflit au lieu d'écraser la vente ; une sauvegarde en cours, elle, tient le
    // verrou de la ligne (lockByProductId) et fait attendre cette réservation.
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
