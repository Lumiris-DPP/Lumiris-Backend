package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.PushSubscriptionRequest;
import com.minoh.lumiris_backend.entity.PushSubscription;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.PushSubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    private PushSubscriptionService service;

    private static final String USER_EMAIL = "client@test.com";
    private User user;

    @BeforeEach
    void setUp() {
        service = new PushSubscriptionService(subscriptionRepository, userRepository);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(USER_EMAIL);
        lenient().when(userRepository.getByEmail(USER_EMAIL)).thenReturn(user);
    }

    @Test
    void subscribe_shouldCreateNewSubscription_whenEndpointUnknown() {
        String endpoint = "https://push.example.com/abc";
        when(subscriptionRepository.findByEndpoint(endpoint)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.subscribe(USER_EMAIL, new PushSubscriptionRequest(endpoint,
                new PushSubscriptionRequest.Keys("p256dh-key", "auth-key")));

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(user);
        assertThat(saved.getValue().getEndpoint()).isEqualTo(endpoint);
        assertThat(saved.getValue().getP256dh()).isEqualTo("p256dh-key");
        assertThat(saved.getValue().getAuth()).isEqualTo("auth-key");
    }

    @Test
    void subscribe_shouldReuseExistingRow_whenEndpointAlreadyKnown() {
        String endpoint = "https://push.example.com/abc";
        PushSubscription existing = new PushSubscription();
        existing.setId(UUID.randomUUID());
        existing.setEndpoint(endpoint);
        when(subscriptionRepository.findByEndpoint(endpoint)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.subscribe(USER_EMAIL, new PushSubscriptionRequest(endpoint,
                new PushSubscriptionRequest.Keys("new-p256dh", "new-auth")));

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
        assertThat(saved.getValue().getUser()).isEqualTo(user);
        assertThat(saved.getValue().getP256dh()).isEqualTo("new-p256dh");
    }

    @Test
    void unsubscribe_shouldDeleteScopedToCurrentUser() {
        String endpoint = "https://push.example.com/abc";

        service.unsubscribe(USER_EMAIL, endpoint);

        verify(subscriptionRepository).deleteByUser_IdAndEndpoint(user.getId(), endpoint);
    }
}
