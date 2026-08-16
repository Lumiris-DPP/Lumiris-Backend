package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceFavoriteRepository extends JpaRepository<MarketplaceFavorite, UUID> {

    Optional<MarketplaceFavorite> findByUser_IdAndProduct_Id(UUID userId, UUID productId);

    // Racine sur le produit pour que le join fetch de l'atelier reste sans ambiguïté, et surtout
    // pour renvoyer le MÊME tuple (produit, score) que toutes les autres requêtes du catalogue :
    // l'assembleur existant le consomme sans rien changer.
    @Query("""
            select p, s
            from MarketplaceProduct p
            join MarketplaceFavorite f on f.product.id = p.id
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where f.user.id = :userId
            order by f.createdAt desc
            """)
    List<Object[]> findScoredByUserId(@Param("userId") UUID userId);

    // Candidats « il n'en reste qu'un » : le seuil porte sur le stock TOTAL de la pièce (le favori
    // suit une annonce, pas une taille).
    @Query("""
            select f from MarketplaceFavorite f
            join fetch f.user
            join fetch f.product p
            join fetch p.artisanProfile
            where p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
              and f.lowStockNotifiedAt is null
              and (select coalesce(sum(v.stock), 0) from MarketplaceProductVariant v
                   where v.product.id = p.id) = :threshold
            """)
    List<MarketplaceFavorite> findLowStockCandidates(@Param("threshold") long threshold);

    // Remise à zéro du détecteur de front dès que le stock repasse au-dessus du seuil : sans elle
    // l'acheteur serait alerté une seule fois dans sa vie, même si la pièce se raréfie à nouveau.
    @Modifying
    @Query("""
            update MarketplaceFavorite f set f.lowStockNotifiedAt = null
            where f.lowStockNotifiedAt is not null
              and f.product.id in (
                  select v.product.id from MarketplaceProductVariant v
                  group by v.product.id having sum(v.stock) > :threshold)
            """)
    int clearLowStockFlags(@Param("threshold") long threshold);

    // Candidats « le prix a baissé » : baisse d'au moins minDropCents ET d'au moins
    // (100 - maxRatioPercent) %. Ratio en arithmétique entière — aucune comparaison flottante en SQL.
    // Un seuil plat seul est du bruit sur un manteau à 400 €, un pourcentage seul en est sur une
    // pièce à 20 €.
    @Query("""
            select f from MarketplaceFavorite f
            join fetch f.user
            join fetch f.product p
            join fetch p.artisanProfile
            where p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
              and p.priceCents <= f.lastPriceCents - :minDropCents
              and p.priceCents * 100 <= f.lastPriceCents * :maxRatioPercent
            """)
    List<MarketplaceFavorite> findPriceDropCandidates(@Param("minDropCents") int minDropCents,
                                                      @Param("maxRatioPercent") int maxRatioPercent);

    // Revendications conditionnelles : le balayage tourne sur chaque instance, et un double tir
    // signifierait un e-mail en double. Même mécanisme que le décrément de stock — on ne notifie que
    // si l'update a effectivement affecté une ligne, le perdant de la course reste silencieux.
    @Modifying
    @Query("update MarketplaceFavorite f set f.lowStockNotifiedAt = :now "
            + "where f.id = :id and f.lowStockNotifiedAt is null")
    int claimLowStock(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update MarketplaceFavorite f set f.lastPriceCents = :newPrice "
            + "where f.id = :id and f.lastPriceCents = :observedPrice")
    int claimPriceDrop(@Param("id") UUID id,
                       @Param("observedPrice") int observedPrice,
                       @Param("newPrice") int newPrice);
}
