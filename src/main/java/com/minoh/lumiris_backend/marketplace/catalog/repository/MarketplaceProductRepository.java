package com.minoh.lumiris_backend.marketplace.catalog.repository;

import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, UUID> {

    @Modifying
    @Query("update MarketplaceProduct p set p.views = p.views + 1 where p.id = :id")
    int incrementViews(@Param("id") UUID id);

    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.id = :id
              and p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
            """)
    List<Object[]> findScoredPublishedById(@Param("id") UUID id);

    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.id in :ids
              and p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
            """)
    List<Object[]> findScoredPublishedByIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.dppForm.id = :dppFormId
              and p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
            """)
    List<Object[]> findScoredPublishedByDpp(@Param("dppFormId") UUID dppFormId);

    @Query("select coalesce(sum(p.views), 0) from MarketplaceProduct p where p.artisanProfile.id = :artisanProfileId")
    long totalViewsByArtisanProfile(@Param("artisanProfileId") UUID artisanProfileId);

    long countByArtisanProfileId(UUID artisanProfileId);

    long countByArtisanProfileIdAndStatus(UUID artisanProfileId,
                                          com.minoh.lumiris_backend.entity.MarketplaceProductStatus status);

    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.artisanProfile.id = :artisanProfileId
            order by p.createdAt desc
            """)
    List<Object[]> findScoredByArtisanProfileId(@Param("artisanProfileId") UUID artisanProfileId);

    Optional<MarketplaceProduct> findByIdAndArtisanProfileId(UUID id, UUID artisanProfileId);

    Optional<MarketplaceProduct> findByDppFormId(UUID dppFormId);

    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
              and (:category is null or lower(p.category) = lower(cast(:category as string)))
              and (:material is null or lower(p.material) = lower(cast(:material as string)))
              and (:origin   is null or lower(p.originCountry) = lower(cast(:origin as string)))
            """)
    List<Object[]> searchPublished(@Param("category") String category,
                                   @Param("material") String material,
                                   @Param("origin") String origin);

    @Query(value = """
            select p.id as id,
                   ts_rank(p.search_vector, websearch_to_tsquery('french', lumiris_unaccent(:q))) as rank
            from marketplace_products p
            where p.status = 'PUBLISHED'
              and p.search_vector @@ websearch_to_tsquery('french', lumiris_unaccent(:q))
              and (cast(:category as text) is null or lower(p.category)       = lower(cast(:category as text)))
              and (cast(:material as text) is null or lower(p.material)       = lower(cast(:material as text)))
              and (cast(:origin   as text) is null or lower(p.origin_country) = lower(cast(:origin   as text)))
            """, nativeQuery = true)
    List<Object[]> searchPublishedTextRanked(@Param("q") String q,
                                             @Param("category") String category,
                                             @Param("material") String material,
                                             @Param("origin") String origin);

    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
              and s.total >= :minTotal
              and (:category is null or lower(p.category) = lower(cast(:category as string)))
            """)
    List<Object[]> suggestCandidates(@Param("minTotal") double minTotal,
                                     @Param("category") String category);
}
