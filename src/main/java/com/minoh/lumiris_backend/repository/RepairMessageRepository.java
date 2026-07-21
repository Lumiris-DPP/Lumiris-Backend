package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RepairMessage;
import com.minoh.lumiris_backend.entity.RepairRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairMessageRepository extends JpaRepository<RepairMessage, UUID> {
    List<RepairMessage> findByRepairRequestOrderByCreatedAtAsc(RepairRequest repairRequest);
}
