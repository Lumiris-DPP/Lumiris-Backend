package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.DashboardInfoResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.entity.IrisScore;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<String> GRADES = List.of("A", "B", "C", "D", "E");
    private static final PageRequest RECENT = PageRequest.of(0, 5);

    private final ArtisanProfileRepository artisanProfileRepository;
    private final DppFormRepository dppFormRepository;
    private final IrisScoreRepository irisScoreRepository;
    private final QuotaService quotaService;

    @Transactional(readOnly = true)
    public DashboardInfoResponse getInfo(User user) {
        UUID userId = user.getId();
        ArtisanProfile profile = artisanProfileRepository.findByUser(user).orElse(null);
        QuotaService.Quota quota = quotaService.forUser(user);
        Map<DppStatus, Long> counts = countByStatus(userId);

        return new DashboardInfoResponse(
                displayName(user, profile),
                profile != null ? profile.getPhotoUrl() : null,
                isProfileComplete(profile),
                counts.getOrDefault(DppStatus.VALID, 0L),
                counts.getOrDefault(DppStatus.INVALID, 0L),
                counts.getOrDefault(DppStatus.DRAFT, 0L),
                averageIrisScore(userId),
                gradeDistribution(userId),
                recentPassports(userId),
                quota.used(),
                quota.limit(),
                0,
                0
        );
    }

    private Map<DppStatus, Long> countByStatus(UUID userId) {
        Map<DppStatus, Long> counts = new EnumMap<>(DppStatus.class);
        for (DppFormRepository.StatusCount row : dppFormRepository.countByStatus(userId)) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }

    private double averageIrisScore(UUID userId) {
        Double average = irisScoreRepository.averageTotal(userId, DppStatus.VALID);
        return average == null ? 0 : Math.round(average * 10) / 10.0;
    }

    private Map<String, Long> gradeDistribution(UUID userId) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        GRADES.forEach(grade -> distribution.put(grade, 0L));
        for (IrisScoreRepository.GradeCount row : irisScoreRepository.countByGrade(userId, DppStatus.DRAFT)) {
            if (distribution.containsKey(row.getGrade())) {
                distribution.put(row.getGrade(), row.getTotal());
            }
        }
        return distribution;
    }

    private List<DashboardInfoResponse.RecentPassport> recentPassports(UUID userId) {
        List<DppForm> forms = dppFormRepository.findByUserIdOrderByUpdatedAtDesc(userId, RECENT);
        Map<UUID, IrisScore> scores = new LinkedHashMap<>();
        for (IrisScore score : irisScoreRepository.findByDppFormIdIn(forms.stream().map(DppForm::getId).toList())) {
            scores.put(score.getDppForm().getId(), score);
        }
        return forms.stream()
                .map(form -> {
                    IrisScore score = scores.get(form.getId());
                    return new DashboardInfoResponse.RecentPassport(
                            form.getId(),
                            form.getProductName(),
                            form.getStatus().name(),
                            score != null ? score.getGrade() : null,
                            score != null ? score.getTotal() : 0,
                            form.getUpdatedAt()
                    );
                })
                .toList();
    }

    private String displayName(User user, ArtisanProfile profile) {
        if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) {
            return profile.getDisplayName();
        }
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail();
    }

    private boolean isProfileComplete(ArtisanProfile profile) {
        return profile != null
                && profile.getStory() != null && !profile.getStory().isBlank()
                && profile.getSpecialties() != null && !profile.getSpecialties().isEmpty()
                && profile.getPhotoUrl() != null && !profile.getPhotoUrl().isBlank();
    }
}
