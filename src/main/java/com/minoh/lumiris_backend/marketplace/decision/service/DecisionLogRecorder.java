package com.minoh.lumiris_backend.marketplace.decision.service;

import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import com.minoh.lumiris_backend.marketplace.decision.repository.MarketplaceDecisionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DecisionLogRecorder {

    private final MarketplaceDecisionLogRepository decisionLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MarketplaceDecisionLog persist(String context, String sortKey, String request, String result) {
        return decisionLogRepository.save(new MarketplaceDecisionLog(context, sortKey, request, result));
    }
}
