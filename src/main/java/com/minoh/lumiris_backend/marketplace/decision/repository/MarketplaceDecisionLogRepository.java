package com.minoh.lumiris_backend.marketplace.decision.repository;

import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Accède aux pistes d’audit des recherches et suggestions. */
public interface MarketplaceDecisionLogRepository extends JpaRepository<MarketplaceDecisionLog, UUID> {
}
