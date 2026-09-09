package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerSource;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairerProfileRepository extends JpaRepository<RepairerProfile, UUID> {

    Optional<RepairerProfile> findByUser(User user);

    Optional<RepairerProfile> findByClaimToken(UUID claimToken);

    Optional<RepairerProfile> findBySourceAndExternalRef(RepairerSource source, String externalRef);

    List<RepairerProfile> findByStatus(RepairerStatus status);

    // Fiches annuaire jamais réclamées, importées il y a longtemps et pas recontactées récemment :
    // à purger (RGPD — on ne garde pas indéfiniment des pros qui n'ont rien demandé).
    @Query("""
            select p from RepairerProfile p
            where p.status = com.minoh.lumiris_backend.entity.RepairerStatus.UNCLAIMED
              and p.user is null
              and p.importedAt < :importedBefore
              and not exists (
                select 1 from RepairerProspectOutreach o
                where o.repairerProfile = p and o.sentAt > :contactedAfter
              )
            """)
    List<RepairerProfile> findPurgeableUnclaimed(@Param("importedBefore") Instant importedBefore,
                                                 @Param("contactedAfter") Instant contactedAfter);

    // ST_DWithin uses the spatial GIST index to pre-filter before computing exact distance; both
    // operate on the geography cast so the radius/distance are in meters. La note moyenne et le
    // délai médian de réponse viennent de sous-requêtes agrégées ; le tri est piloté par :sort
    // ('distance' par défaut, 'rating', 'responsiveness'), la distance restant le départage.
    @Query(value = """
            SELECT r.id, r.display_name, r.company_name, r.specialties, r.zones, r.schedule,
                   r.address, r.city, r.region,
                   ST_Distance(r.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m,
                   ST_Y(r.location) AS lat, ST_X(r.location) AS lng,
                   COALESCE(rv.avg_rating, 0)   AS avg_rating,
                   COALESCE(rv.review_count, 0) AS review_count,
                   med.median_response_seconds
            FROM repairer_profiles r
            LEFT JOIN (
                SELECT repairer_profile_id, AVG(rating) AS avg_rating, COUNT(*) AS review_count
                FROM repairer_reviews GROUP BY repairer_profile_id
            ) rv ON rv.repairer_profile_id = r.id
            LEFT JOIN (
                SELECT repairer_profile_id,
                       EXTRACT(EPOCH FROM percentile_cont(0.5) WITHIN GROUP (
                           ORDER BY (quote_submitted_at - created_at))) AS median_response_seconds
                FROM repair_requests WHERE quote_submitted_at IS NOT NULL
                GROUP BY repairer_profile_id
            ) med ON med.repairer_profile_id = r.id
            WHERE r.status = 'VERIFIED'
              AND r.location IS NOT NULL
              AND (:specialty IS NULL OR :specialty = ANY(r.specialties))
              AND ST_DWithin(r.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
            ORDER BY
              CASE WHEN :sort = 'rating' THEN COALESCE(rv.avg_rating, 0) END DESC,
              CASE WHEN :sort = 'responsiveness' THEN med.median_response_seconds END ASC NULLS LAST,
              distance_m ASC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> searchNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("specialty") String specialty,
            @Param("radiusMeters") double radiusMeters,
            @Param("sort") String sort,
            @Param("size") int size,
            @Param("offset") int offset
    );
}
