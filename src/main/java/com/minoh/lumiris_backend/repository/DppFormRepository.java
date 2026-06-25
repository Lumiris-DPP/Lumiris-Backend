package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DppFormRepository extends JpaRepository<DppForm, UUID> {

    List<DppForm> findByUserId(UUID userId);
}
