package com.minoh.lumiris_backend.marketplace.decision.repository;

import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceDecisionLogRepository extends JpaRepository<MarketplaceDecisionLog, UUID> {
}
