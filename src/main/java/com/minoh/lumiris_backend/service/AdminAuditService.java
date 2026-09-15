package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.AdminAuditEntryResponse;
import com.minoh.lumiris_backend.entity.AdminAuditLog;
import com.minoh.lumiris_backend.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    public static final String TARGET_REPAIRER = "repairer";
    public static final String TARGET_ARTISAN = "artisan";

    private final AdminAuditLogRepository repo;

    @Transactional
    public void record(String actorEmail, String action, String targetType, String targetId, String detail) {
        repo.save(new AdminAuditLog(actorEmail, action, targetType, targetId, detail));
    }

    @Transactional(readOnly = true)
    public List<AdminAuditEntryResponse> recent(String targetType, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var slice = targetType == null || targetType.isBlank()
                ? repo.findAllByOrderByOccurredAtDesc(pageable)
                : repo.findByTargetTypeOrderByOccurredAtDesc(targetType, pageable);
        return slice.map(AdminAuditEntryResponse::from).getContent();
    }
}
