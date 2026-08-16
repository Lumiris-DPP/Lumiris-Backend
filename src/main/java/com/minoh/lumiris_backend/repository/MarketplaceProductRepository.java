package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.MarketplaceProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, UUID> {

    // Vue de fiche produit (VISION) — incrément atomique, fire-and-forget.
    @Modifying
    @Query("update MarketplaceProduct p set p.views = p.views + 1 where p.id = :id")
    int incrementViews(@Param("id") UUID id);

    // Fiche produit publiée unitaire (VISION) — évite de scanner tout le catalogue pour un deep-link.
    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.id = :id
              and p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
            """)
    List<Object[]> findScoredPublishedById(@Param("id") UUID id);

    // Hydratation du panier : les seules fiches dont l'acheteur a besoin, plutôt que tout le
    // catalogue. Un produit dépublié entre-temps sort du résultat — c'est ce qui permet au panier
    // de dire lequel est devenu indisponible.
    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.id in :ids
              and p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
            """)
    List<Object[]> findScoredPublishedByIds(@Param("ids") Collection<UUID> ids);

    // Pont scan → achat : le produit publié lié à un passeport scanné (unifie les 2 modèles d'achat).
    @Query("""
            select p, s
            from MarketplaceProduct p
            join fetch p.artisanProfile
            left join IrisScore s on s.dppForm.id = p.dppForm.id
            where p.dppForm.id = :dppFormId
              and p.status = com.minoh.lumiris_backend.entity.MarketplaceProductStatus.PUBLISHED
            """)
    List<Object[]> findScoredPublishedByDpp(@Param("dppFormId") UUID dppFormId);

    // Total des vues de toutes les fiches d'un atelier — KPI tableau de bord.
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

    // Recherche plein texte : le vecteur pondéré est maintenu par Postgres (colonne générée), l'index
    // GIN porte le prédicat. On ne renvoie que (id, rang) — l'hydratation réutilise
    // findScoredPublishedByIds, qui porte déjà le join fetch de l'atelier et la jointure au score.
    // websearch_to_tsquery et JAMAIS to_tsquery : cet endpoint est public et non authentifié, et
    // to_tsquery lève sur une entrée malformée (« veste & » deviendrait une 500 à la demande).
    // Les casts explicites sont indispensables en SQL natif, sans quoi Postgres ne peut pas
    // déterminer le type des paramètres nuls.
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
