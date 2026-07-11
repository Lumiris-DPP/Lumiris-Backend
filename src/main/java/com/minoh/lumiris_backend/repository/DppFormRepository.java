package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DppFormRepository extends JpaRepository<DppForm, UUID> {

    List<DppForm> findByUserId(UUID userId);

    // Used by the billing QuotaService to count a user's existing passports against their plan quota.
    long countByUserId(UUID userId);

    Optional<DppForm> findByPublicCode(String publicCode);

    boolean existsByPublicCode(String publicCode);
}
