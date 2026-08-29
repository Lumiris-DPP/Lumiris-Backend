package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.NotificationPreferenceUpdateRequest;
import com.minoh.lumiris_backend.dto.out.NotificationPreferenceResponse;
import com.minoh.lumiris_backend.entity.NotificationCategory;
import com.minoh.lumiris_backend.entity.NotificationPreference;
import com.minoh.lumiris_backend.entity.NotificationType;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.NotificationPreferenceRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationPreferenceService service;

    private static final String USER_EMAIL = "client@test.com";

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(USER_EMAIL);
        lenient().when(userRepository.getByEmail(USER_EMAIL)).thenReturn(user);
    }

    @Test
    void isEmailEnabled_shouldDefaultToTrue_whenNoPreferenceRow() {
        when(preferenceRepository.findByUser_IdAndCategory(user.getId(), NotificationCategory.PAYMENTS))
                .thenReturn(Optional.empty());

        assertThat(service.isEmailEnabled(user, NotificationType.PAYMENT_SUCCEEDED)).isTrue();
    }

    @Test
    void isEmailEnabled_shouldReturnFalse_whenExplicitlyDisabled() {
        NotificationPreference pref = new NotificationPreference();
        pref.setCategory(NotificationCategory.PAYMENTS);
        pref.setEmailEnabled(false);
        pref.setPushEnabled(true);
        when(preferenceRepository.findByUser_IdAndCategory(user.getId(), NotificationCategory.PAYMENTS))
                .thenReturn(Optional.of(pref));

        assertThat(service.isEmailEnabled(user, NotificationType.PAYMENT_FAILED)).isFalse();
    }

    @Test
    void list_shouldReturnEveryCategory_defaultingUncoveredOnesToSubscribed() {
        NotificationPreference disabled = new NotificationPreference();
        disabled.setCategory(NotificationCategory.PAYMENTS);
        disabled.setEmailEnabled(false);
        disabled.setPushEnabled(false);
        when(preferenceRepository.findByUser_Id(user.getId())).thenReturn(List.of(disabled));

        List<NotificationPreferenceResponse> preferences = service.list(USER_EMAIL);

        assertThat(preferences).hasSize(NotificationCategory.values().length);
        assertThat(preferences)
                .filteredOn(p -> p.category().equals("PAYMENTS"))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.emailEnabled()).isFalse();
                    assertThat(p.pushEnabled()).isFalse();
                });
        assertThat(preferences)
                .filteredOn(p -> p.category().equals("ORDERS"))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.emailEnabled()).isTrue();
                    assertThat(p.pushEnabled()).isTrue();
                });
    }

    @Test
    void update_shouldCreateNewPreference_whenNoneExists() {
        when(preferenceRepository.findByUser_IdAndCategory(user.getId(), NotificationCategory.PASSPORT))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse response = service.update(USER_EMAIL, NotificationCategory.PASSPORT,
                new NotificationPreferenceUpdateRequest(false, true));

        ArgumentCaptor<NotificationPreference> saved = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(user);
        assertThat(saved.getValue().getCategory()).isEqualTo(NotificationCategory.PASSPORT);
        assertThat(saved.getValue().isEmailEnabled()).isFalse();
        assertThat(saved.getValue().isPushEnabled()).isTrue();
        assertThat(response.emailEnabled()).isFalse();
    }

    @Test
    void update_shouldOverwriteExistingPreference() {
        NotificationPreference existing = new NotificationPreference();
        existing.setUser(user);
        existing.setCategory(NotificationCategory.ATELIER);
        existing.setEmailEnabled(true);
        existing.setPushEnabled(true);
        when(preferenceRepository.findByUser_IdAndCategory(user.getId(), NotificationCategory.ATELIER))
                .thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse response = service.update(USER_EMAIL, NotificationCategory.ATELIER,
                new NotificationPreferenceUpdateRequest(false, false));

        assertThat(response.emailEnabled()).isFalse();
        assertThat(response.pushEnabled()).isFalse();
        assertThat(existing.isEmailEnabled()).isFalse();
    }
}
