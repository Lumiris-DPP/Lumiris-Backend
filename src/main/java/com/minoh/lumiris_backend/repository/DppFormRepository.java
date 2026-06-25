package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DppFormRepository extends JpaRepository<DppForm, UUID> {

    @Query("""
        SELECT d FROM DppForm d
        LEFT JOIN FETCH d.materials
        LEFT JOIN FETCH d.careInstructions
        LEFT JOIN FETCH d.certifications
        WHERE d.id = :id
    """)
    Optional<DppForm> findByIdWithDetails(@Param("id") UUID id);

    List<DppForm> findByUserId(UUID userId);
}
