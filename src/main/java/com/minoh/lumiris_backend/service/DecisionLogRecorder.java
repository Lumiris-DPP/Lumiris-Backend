package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import com.minoh.lumiris_backend.repository.MarketplaceDecisionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Enregistre une décision de classement indépendamment de la recherche.
@Component
@RequiredArgsConstructor
public class DecisionLogRecorder {

    private final MarketplaceDecisionLogRepository decisionLogRepository;

    // Sauvegarde la décision dans une transaction indépendante.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MarketplaceDecisionLog persist(String context, String sortKey, String request, String result) {
        return decisionLogRepository.save(new MarketplaceDecisionLog(context, sortKey, request, result));
    }
}
