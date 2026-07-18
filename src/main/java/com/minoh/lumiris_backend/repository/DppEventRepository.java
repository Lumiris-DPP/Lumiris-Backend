package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DppEventRepository extends JpaRepository<DppEvent, UUID> {
    List<DppEvent> findByDppFormIdOrderByOccurredAtDesc(UUID dppFormId);
}
