package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DppFormRepository extends JpaRepository<DppForm, UUID> {

    List<DppForm> findByUserId(UUID userId);

    // Used by the billing QuotaService to count a user's existing passports against their plan quota.
    long countByUserId(UUID userId);

    // Drafts don't consume the passport quota — only published forms count.
    long countByUserIdAndStatusNot(UUID userId, DppStatus status);

    Optional<DppForm> findByPublicCode(String publicCode);

    boolean existsByPublicCode(String publicCode);

    @Query("""
            select f from DppForm f
            where f.status = :status
              and not exists (select 1 from IrisScore s where s.dppForm = f)
            """)
    List<DppForm> findWithoutIrisScore(@Param("status") DppStatus status);
}
