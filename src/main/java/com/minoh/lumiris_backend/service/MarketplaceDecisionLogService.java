package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.dto.out.DecisionLogResponse;
import com.minoh.lumiris_backend.entity.MarketplaceDecisionLog;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.repository.MarketplaceDecisionLogRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Piste d'audit des tris du catalogue public : chaque recherche et chaque suggestion laisse une
// décision horodatée, qui prouve que la commission n'a pas compté, relisible par l'audit interne.
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceDecisionLogService {

    // Borne la taille d'une ligne de log de décision : un SEARCH peut trier tout le catalogue,
    // et ces lignes append-only sont écrites par un endpoint public (croissance non prunable).
    private static final int MAX_DECISION_LOG_ENTRIES = 500;

    private final MarketplaceDecisionLogRepository decisionLogRepository;
    private final DecisionLogRecorder decisionLogRecorder;
    private final UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Best-effort : un échec d'écriture de la piste d'audit ne doit JAMAIS faire échouer ni rollback la
    // lecture (search/suggest). Deux choses le garantissent : DecisionLogRecorder écrit dans sa propre
    // transaction (REQUIRES_NEW) — c'est aussi ce qui permet d'écrire depuis une recherche en lecture
    // seule —, et l'exception est interceptée ici. En cas d'échec on renvoie un log transitoire non
    // persisté (id null) plutôt qu'une 500.
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

    // Audit indépendant réservé aux ADMIN : la piste d'audit n'est ni publique ni rattachée à un
    // artisan (aucun ownership à vérifier), on restreint donc au rôle d'audit interne.
    @Transactional(readOnly = true)
    public DecisionLogResponse getDecisionLog(String email, UUID id) {
        requireAdmin(email);
        MarketplaceDecisionLog entity = decisionLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log de décision introuvable"));
        List<DecisionLogResponse.Entry> ranked = readEntries(entity.getResult());
        return new DecisionLogResponse(entity.getId(), entity.getContext(), entity.getSortKey(),
                entity.isCommissionConsidered(), entity.getCreatedAt(), ranked);
    }

    // Borne le nombre d'entrées écrites dans le log (taille de ligne JSONB append-only, non prunable).
    private static List<DecisionLogResponse.Entry> capForLog(List<DecisionLogResponse.Entry> ranked) {
        return ranked.size() <= MAX_DECISION_LOG_ENTRIES
                ? ranked
                : ranked.subList(0, MAX_DECISION_LOG_ENTRIES);
    }

    // Refuse l'utilisateur courant s'il n'appartient pas à l'audit interne.
    private void requireAdmin(String email) {
        User user = userRepository.getByEmail(email);
        if (user.getRole() != UserRole.ADMIN) {
            throw new RoleNotAllowedException("Consultation réservée à l'audit interne (ADMIN).");
        }
    }

    // Sérialise la requête ou le classement pour la ligne JSONB.
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation du log de décision échouée", e);
        }
    }

    // Relit le classement enregistré.
    private List<DecisionLogResponse.Entry> readEntries(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<DecisionLogResponse.Entry>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Lecture du log de décision échouée", e);
        }
    }
}
