package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.NotificationPreferenceUpdateRequest;
import com.minoh.lumiris_backend.dto.out.NotificationPreferenceResponse;
import com.minoh.lumiris_backend.entity.NotificationCategory;
import com.minoh.lumiris_backend.entity.NotificationPreference;
import com.minoh.lumiris_backend.entity.NotificationType;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.NotificationPreferenceRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Absence de ligne pour (user, category) == abonné aux deux canaux (comportement par défaut) ;
// seules les catégories explicitement désactivées sont persistées.
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public boolean isEmailEnabled(User user, NotificationType type) {
        return preferenceRepository
                .findByUser_IdAndCategory(user.getId(), NotificationCategory.of(type))
                .map(NotificationPreference::isEmailEnabled)
                .orElse(true);
    }

    public boolean isPushEnabled(User user, NotificationType type) {
        return preferenceRepository
                .findByUser_IdAndCategory(user.getId(), NotificationCategory.of(type))
                .map(NotificationPreference::isPushEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> list(String userEmail) {
        User user = userRepository.getByEmail(userEmail);
        Map<NotificationCategory, NotificationPreference> byCategory = new EnumMap<>(NotificationCategory.class);
        preferenceRepository.findByUser_Id(user.getId())
                .forEach(pref -> byCategory.put(pref.getCategory(), pref));

        return Arrays.stream(NotificationCategory.values())
                .map(category -> {
                    NotificationPreference pref = byCategory.get(category);
                    return new NotificationPreferenceResponse(
                            category.name(),
                            pref == null || pref.isEmailEnabled(),
                            pref == null || pref.isPushEnabled());
                })
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse update(String userEmail, NotificationCategory category,
                                                  NotificationPreferenceUpdateRequest request) {
        User user = userRepository.getByEmail(userEmail);
        NotificationPreference pref = preferenceRepository.findByUser_IdAndCategory(user.getId(), category)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setUser(user);
                    created.setCategory(category);
                    return created;
                });
        pref.setEmailEnabled(request.emailEnabled());
        pref.setPushEnabled(request.pushEnabled());
        preferenceRepository.save(pref);
        return new NotificationPreferenceResponse(category.name(), pref.isEmailEnabled(), pref.isPushEnabled());
    }
}
