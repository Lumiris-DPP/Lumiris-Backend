package com.minoh.lumiris_backend.service.stripe;

import com.minoh.lumiris_backend.config.stripe.StripeProperties;
import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.domain.StripeSubscriptionStatus;
import com.minoh.lumiris_backend.dto.out.SubscriptionStateResponse;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateTest {

    private static final String EMAIL = "retoucheur@lumiris.test";

    private static final StripeProperties PROPERTIES =
            new StripeProperties(null, "pk_test", null, false, null, null);

    @Mock
    private StripeCustomerService customerService;

    @Mock
    private StripeCatalogService catalogService;

    @Mock
    private SubscriptionSyncService syncService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuotaService quotaService;

    private SubscriptionService subscriptionService;

    private User user;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                PROPERTIES, customerService, catalogService, syncService,
                subscriptionRepository, userRepository, quotaService);
        user = new User();
        user.setId(UUID.randomUUID());
        when(userRepository.getByEmail(EMAIL)).thenReturn(user);
    }

    private void subscribed(PlanTier tier, String status) {
        UserSubscription sub = new UserSubscription();
        sub.setPlanTier(tier);
        sub.setStatus(status);
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.of(sub));
        quota(tier, tier.grantsPassports() && StripeSubscriptionStatus.isActive(status));
    }

    private void notSubscribed() {
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        quota(null, false);
    }

    private void quota(PlanTier tier, boolean passportEligible) {
        when(quotaService.forUser(user)).thenReturn(
                new QuotaService.Quota(passportEligible, tier, 0L, 0, false, passportEligible, null));
    }

    @Test
    void localSubscriberHasLiveSubscriptionWithoutPassportEligibility() {
        subscribed(PlanTier.LOCAL, StripeSubscriptionStatus.ACTIVE);

        SubscriptionStateResponse state = subscriptionService.getState(EMAIL);

        assertThat(state.hasActiveSubscription()).isFalse();
        assertThat(state.hasLiveSubscription()).isTrue();
    }

    @Test
    void atelierSubscriberHasBoth() {
        subscribed(PlanTier.ATELIER_SOLO, StripeSubscriptionStatus.ACTIVE);

        SubscriptionStateResponse state = subscriptionService.getState(EMAIL);

        assertThat(state.hasActiveSubscription()).isTrue();
        assertThat(state.hasLiveSubscription()).isTrue();
    }

    @Test
    void unpaidSubscriberStaysLiveSoTheAddonMatchesTheBackendGuard() {
        subscribed(PlanTier.ATELIER_SOLO, StripeSubscriptionStatus.PAST_DUE);

        SubscriptionStateResponse state = subscriptionService.getState(EMAIL);

        assertThat(state.hasActiveSubscription()).isFalse();
        assertThat(state.hasLiveSubscription()).isTrue();
    }

    @Test
    void accountWithoutSubscriptionHasNeither() {
        notSubscribed();

        SubscriptionStateResponse state = subscriptionService.getState(EMAIL);

        assertThat(state.hasActiveSubscription()).isFalse();
        assertThat(state.hasLiveSubscription()).isFalse();
    }
}
