package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RepairerProspectOutreach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairerProspectOutreachRepository extends JpaRepository<RepairerProspectOutreach, UUID> {

    Optional<RepairerProspectOutreach> findByToken(UUID token);

    // Prospects encore ouverts, joignables pour une relance : jamais réclamés ni désinscrits,
    // sous le plafond de relances, et dont le dernier contact remonte à assez longtemps.
    @Query("""
            select o from RepairerProspectOutreach o
            where o.claimedAt is null
              and o.unsubscribedAt is null
              and o.contactCount < :maxContacts
              and o.lastContactedAt < :before
            """)
    List<RepairerProspectOutreach> findDueForFollowUp(@Param("maxContacts") int maxContacts,
                                                      @Param("before") Instant before);
}
