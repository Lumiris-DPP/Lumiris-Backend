package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairerProfileRepository extends JpaRepository<RepairerProfile, UUID> {

    Optional<RepairerProfile> findByUser(User user);

    Optional<RepairerProfile> findByClaimToken(UUID claimToken);

    List<RepairerProfile> findByStatus(RepairerStatus status);

    // ST_DWithin uses the spatial GIST index to pre-filter before computing exact
    // distance; both operate on the geography cast so the radius/distance are in meters.
    @Query(value = """
            SELECT r.id, r.display_name, r.company_name, r.specialties, r.zones, r.schedule,
                   r.address, r.city, r.region,
                   ST_Distance(r.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m,
                   ST_Y(r.location) AS lat, ST_X(r.location) AS lng
            FROM repairer_profiles r
            WHERE r.status = 'VERIFIED'
              AND r.location IS NOT NULL
              AND (:specialty IS NULL OR :specialty = ANY(r.specialties))
              AND ST_DWithin(r.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
            ORDER BY distance_m ASC
            """, nativeQuery = true)
    List<Object[]> searchNearby(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("specialty") String specialty,
            @Param("radiusMeters") double radiusMeters
    );
}
