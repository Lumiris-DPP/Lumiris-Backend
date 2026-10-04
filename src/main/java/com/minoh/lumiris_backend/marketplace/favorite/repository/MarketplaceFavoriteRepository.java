package com.minoh.lumiris_backend.marketplace.favorite.repository;

import com.minoh.lumiris_backend.entity.MarketplaceFavorite;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceFavoriteRepository extends JpaRepository<MarketplaceFavorite, UUID> {

    Optional<MarketplaceFavorite> findByUser_IdAndProduct_Id(UUID userId, UUID productId);

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

    @Modifying
    @Query("""
            update MarketplaceFavorite f set f.lowStockNotifiedAt = null
            where f.lowStockNotifiedAt is not null
              and f.product.id in (
                  select v.product.id from MarketplaceProductVariant v
                  group by v.product.id having sum(v.stock) > :threshold)
            """)
    int clearLowStockFlags(@Param("threshold") long threshold);

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
