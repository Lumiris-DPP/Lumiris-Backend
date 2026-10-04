package com.minoh.lumiris_backend.marketplace.decision.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.marketplace.decision.dto.out.DecisionLogResponse;
import com.minoh.lumiris_backend.marketplace.decision.repository.MarketplaceDecisionLogRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceDecisionLogService {

    private static final int MAX_DECISION_LOG_ENTRIES = 500;

    private final MarketplaceDecisionLogRepository decisionLogRepository;
    private final DecisionLogRecorder decisionLogRecorder;
    private final UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DecisionLogResponse record(String context, String sortKey, Object requestEcho,
                                      List<DecisionLogResponse.Entry> ranked) {
        List<DecisionLogResponse.Entry> kept = capForLog(ranked);
        try {
            MarketplaceDecisionLog saved = decisionLogRecorder.persist(
                    context, sortKey, writeJson(requestEcho), writeJson(kept));
            return new DecisionLogResponse(saved.getId(), context, sortKey,
                    saved.isCommissionConsidered(), saved.getCreatedAt(), kept);
        } catch (RuntimeException e) {
            log.warn("Persistance du log de décision {} échouée ({}) ; log transitoire renvoyé",
                    context, e.getClass().getSimpleName());
            return new DecisionLogResponse(null, context, sortKey, false, Instant.now(), kept);
        }
    }

    @Transactional(readOnly = true)
    public DecisionLogResponse getDecisionLog(String email, UUID id) {
        requireAdmin(email);
        MarketplaceDecisionLog entity = decisionLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log de décision introuvable"));
        List<DecisionLogResponse.Entry> ranked = readEntries(entity.getResult());
        return new DecisionLogResponse(entity.getId(), entity.getContext(), entity.getSortKey(),
                entity.isCommissionConsidered(), entity.getCreatedAt(), ranked);
    }

    private static List<DecisionLogResponse.Entry> capForLog(List<DecisionLogResponse.Entry> ranked) {
        return ranked.size() <= MAX_DECISION_LOG_ENTRIES
                ? ranked
                : ranked.subList(0, MAX_DECISION_LOG_ENTRIES);
    }

    private void requireAdmin(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ADMIN) {
            throw new RoleNotAllowedException("Consultation réservée à l'audit interne (ADMIN).");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation du log de décision échouée", e);
        }
    }

    private List<DecisionLogResponse.Entry> readEntries(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<DecisionLogResponse.Entry>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Lecture du log de décision échouée", e);
        }
    }
}
