package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// RGPD — deuxième temps de l'effacement. DELETE /me pose deleted_at ; ce balayage anonymise
// définitivement les comptes dont la fenêtre de rétention (30 j) est écoulée. Quotidien : une
// échéance à l'échelle du mois, pas un événement.
//
// ponytail: pas de verrou distribué. Aligné sur les autres schedulers du projet (un seul
// réplica applicatif). anonymizeAccount est idempotent, donc au pire un double passage ne casse
// rien. Ajouter ShedLock si l'API passe multi-réplicas.
@Component
@RequiredArgsConstructor
public class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);
    private static final long DAILY_MS = 24 * 60 * 60 * 1000L;

    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${lumiris.rgpd.soft-delete-retention-days:30}")
    private int retentionDays;

    @Scheduled(fixedDelay = DAILY_MS, initialDelay = 10 * 60 * 1000L)
    public void purgeExpiredSoftDeletedAccounts() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<User> due = userRepository.findByDeletedAtBeforeAndAnonymizedAtIsNull(cutoff);
        for (User user : due) {
            authService.anonymizeAccount(user);
        }
        if (!due.isEmpty()) {
            log.info("RGPD : {} compte(s) anonymisé(s) après la fenêtre de rétention", due.size());
        }
    }
}
