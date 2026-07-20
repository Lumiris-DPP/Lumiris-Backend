package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.dto.out.AtelierStatsResponse;
import com.minoh.lumiris_backend.entity.PassportAnalyticsEvent;
import com.minoh.lumiris_backend.entity.PassportAnalyticsEventType;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.SubscriptionRequiredException;
import com.minoh.lumiris_backend.repository.PassportAnalyticsEventRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AtelierStatsService {

    private final PassportAnalyticsEventRepository passportAnalyticsEventRepository;
    private final DppFormRepository dppFormRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional
    public void track(String publicCode, String rawType) {
        PassportAnalyticsEventType type = PassportAnalyticsEventType.valueOf(rawType.toUpperCase());
        DppForm form = dppFormRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new ResourceNotFoundException("Passeport introuvable"));
        passportAnalyticsEventRepository.save(new PassportAnalyticsEvent(form, type));
    }

    // Called from the (read-only) public DPP lookup, so a "scan" is tracked with zero extra
    // integration work from VISION/WEB. Runs in its own transaction, independent of the caller's.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackScan(DppForm form) {
        passportAnalyticsEventRepository.save(new PassportAnalyticsEvent(form, PassportAnalyticsEventType.SCAN));
    }

    @Transactional(readOnly = true)
    public AtelierStatsResponse getStats(String userEmail, Instant from, Instant to) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        UserSubscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
        boolean active = sub != null && sub.isActive();
        if (!active) {
            throw new SubscriptionRequiredException(
                    "Un abonnement ATELIER actif est requis pour consulter les statistiques.");
        }
        boolean advancedUnlocked = sub.getPlanTier() == PlanTier.ATELIER_PLUS;

        Instant effectiveFrom = from != null ? from : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant effectiveTo = to != null ? to : Instant.now();

        Map<UUID, long[]> byPassport = new LinkedHashMap<>();
        Map<UUID, String[]> passportMeta = new LinkedHashMap<>();
        long totalScans = 0, totalViews = 0, totalClicks = 0, totalConversions = 0;

        for (Object[] row : passportAnalyticsEventRepository.aggregateByArtisan(user.getId(), effectiveFrom, effectiveTo)) {
            UUID dppFormId = (UUID) row[0];
            String publicCode = (String) row[1];
            String productName = (String) row[2];
            PassportAnalyticsEventType type = (PassportAnalyticsEventType) row[3];
            long count = (Long) row[4];

            passportMeta.putIfAbsent(dppFormId, new String[]{publicCode, productName});
            long[] counts = byPassport.computeIfAbsent(dppFormId, k -> new long[4]);
            int idx = switch (type) {
                case SCAN -> 0;
                case VIEW -> 1;
                case SUGGESTION_CLICK -> 2;
                case CONVERSION -> 3;
            };
            counts[idx] += count;

            switch (type) {
                case SCAN -> totalScans += count;
                case VIEW -> totalViews += count;
                case SUGGESTION_CLICK -> totalClicks += count;
                case CONVERSION -> totalConversions += count;
            }
        }

        var totals = new AtelierStatsResponse.Totals(totalScans, totalViews, totalClicks, totalConversions);

        var breakdown = advancedUnlocked
                ? byPassport.entrySet().stream()
                    .map(entry -> {
                        String[] meta = passportMeta.get(entry.getKey());
                        long[] c = entry.getValue();
                        return new AtelierStatsResponse.PassportBreakdown(
                                entry.getKey(), meta[0], meta[1], c[0], c[1], c[2], c[3]);
                    })
                    .toList()
                : java.util.List.<AtelierStatsResponse.PassportBreakdown>of();

        return new AtelierStatsResponse(effectiveFrom, effectiveTo, totals, !advancedUnlocked, breakdown);
    }
}
