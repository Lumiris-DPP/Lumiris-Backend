package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppScanNotificationThrottle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface DppScanNotificationThrottleRepository extends JpaRepository<DppScanNotificationThrottle, UUID> {

    // Upsert atomique : gagne la ligne (insert, ou update si la fenêtre est expirée) seulement si
    // aucune notification n'a été envoyée depuis :threshold — sinon 0 ligne affectée, l'appelant
    // sait qu'il doit s'abstenir.
    @Modifying
    @Query(value = """
            insert into dpp_scan_notification_throttle (dpp_form_id, last_notified_at)
            values (:formId, :now)
            on conflict (dpp_form_id) do update
                set last_notified_at = :now
                where dpp_scan_notification_throttle.last_notified_at < :threshold
            """, nativeQuery = true)
    int claim(@Param("formId") UUID formId, @Param("now") Instant now, @Param("threshold") Instant threshold);
}
