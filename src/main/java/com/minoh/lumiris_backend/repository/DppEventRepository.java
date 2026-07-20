package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.DppEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DppEventRepository extends JpaRepository<DppEvent, UUID> {
    List<DppEvent> findByDppFormIdOrderByOccurredAtDesc(UUID dppFormId);

    @Modifying
    @Query("delete from DppEvent e where e.dppForm.id = :dppFormId")
    void deleteByDppFormId(@Param("dppFormId") UUID dppFormId);
}
