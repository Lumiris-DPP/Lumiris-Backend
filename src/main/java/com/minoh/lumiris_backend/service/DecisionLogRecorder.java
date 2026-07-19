package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import com.minoh.lumiris_backend.repository.MarketplaceDecisionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Persiste le log de décision dans une transaction SÉPARÉE (REQUIRES_NEW) : l'écriture de la
// piste d'audit ne doit jamais faire échouer — ni rollback — la lecture (search/suggest).
// Isolée dans son propre bean pour que la propagation transactionnelle Spring s'applique.
@Component
@RequiredArgsConstructor
public class DecisionLogRecorder {

    private final MarketplaceDecisionLogRepository decisionLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MarketplaceDecisionLog persist(String context, String sortKey, String request, String result) {
        return decisionLogRepository.save(new MarketplaceDecisionLog(context, sortKey, request, result));
    }
}
