package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findByTargetTypeOrderByOccurredAtDesc(String targetType, Pageable pageable);

    Page<AdminAuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
