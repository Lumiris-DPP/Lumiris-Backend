package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RepairerCoverageGap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RepairerCoverageGapRepository extends JpaRepository<RepairerCoverageGap, UUID> {

    // Agrégat par maille ~11 km (arrondi 0,1°) : où la demande sans réponse se concentre.
    @Query(value = """
            SELECT round(lat::numeric, 1) AS glat, round(lng::numeric, 1) AS glng,
                   COUNT(*) AS n, MAX(occurred_at) AS last_seen
            FROM repairer_coverage_gap
            WHERE occurred_at >= :since
            GROUP BY glat, glng
            ORDER BY n DESC
            """, nativeQuery = true)
    List<Object[]> aggregateSince(@Param("since") Instant since);
}
