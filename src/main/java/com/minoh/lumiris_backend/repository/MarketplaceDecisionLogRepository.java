package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MarketplaceDecisionLogRepository extends JpaRepository<MarketplaceDecisionLog, UUID> {
}
