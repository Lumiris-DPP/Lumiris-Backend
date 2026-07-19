package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, UUID> {

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
