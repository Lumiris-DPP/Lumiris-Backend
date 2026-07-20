package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.PassportAnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PassportAnalyticsEventRepository extends JpaRepository<PassportAnalyticsEvent, UUID> {

    // One row per (passport, event type) for a given artisan and period — the service
    // sums across passports for basic totals, and keeps the per-passport rows for the
    // ATELIER+ breakdown. Aggregated in SQL: no individual event/PII ever leaves the DB.
    @Query("""
            select e.dppForm.id, e.dppForm.publicCode, e.dppForm.productName, e.eventType, count(e)
            from PassportAnalyticsEvent e
            where e.dppForm.user.id = :userId
              and e.occurredAt >= :from
              and e.occurredAt < :to
            group by e.dppForm.id, e.dppForm.publicCode, e.dppForm.productName, e.eventType
            """)
    List<Object[]> aggregateByArtisan(@Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);
}
