package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.exception.QuotaExceededException;
import com.minoh.lumiris_backend.exception.SubscriptionRequiredException;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    private DppFormRepository dppFormRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private QuotaService quotaService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
    }

    private void usage(long count) {
        lenient().when(dppFormRepository.countByUserId(user.getId())).thenReturn(count);
    }

    private void subscription(PlanTier tier, String status) {
        UserSubscription sub = new UserSubscription();
        sub.setPlanTier(tier);
        sub.setStatus(status);
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.of(sub));
    }

    private void noSubscription() {
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
    }

    @Test
    void noSubscription_blocksWithSubscriptionRequired() {
        usage(0);
        noSubscription();

        QuotaService.Quota q = quotaService.forUser(user);

        assertThat(q.hasActiveSubscription()).isFalse();
        assertThat(q.canCreate()).isFalse();
        assertThat(q.reason()).isEqualTo(QuotaService.REASON_SUBSCRIPTION_REQUIRED);
    }

    @Test
    void canceledSubscription_blocksWithSubscriptionRequired() {
        usage(3);
        subscription(PlanTier.ATELIER_SOLO, "canceled");

        QuotaService.Quota q = quotaService.forUser(user);

        assertThat(q.hasActiveSubscription()).isFalse();
        assertThat(q.reason()).isEqualTo(QuotaService.REASON_SUBSCRIPTION_REQUIRED);
    }

    @Test
    void activeSoloUnderLimit_allows() {
        usage(10);
        subscription(PlanTier.ATELIER_SOLO, "active");

        QuotaService.Quota q = quotaService.forUser(user);

        assertThat(q.hasActiveSubscription()).isTrue();
        assertThat(q.canCreate()).isTrue();
        assertThat(q.limit()).isEqualTo(50);
        assertThat(q.unlimited()).isFalse();
        assertThat(q.reason()).isNull();
    }

    @Test
    void activeSoloAtLimit_blocksWithQuotaExceeded() {
        usage(50);
        subscription(PlanTier.ATELIER_SOLO, "active");

        QuotaService.Quota q = quotaService.forUser(user);

        assertThat(q.canCreate()).isFalse();
        assertThat(q.reason()).isEqualTo(QuotaService.REASON_QUOTA_EXCEEDED);
    }

    @Test
    void trialingStudio_isTreatedAsActive() {
        usage(120);
        subscription(PlanTier.ATELIER_STUDIO, "trialing");

        QuotaService.Quota q = quotaService.forUser(user);

        assertThat(q.hasActiveSubscription()).isTrue();
        assertThat(q.canCreate()).isTrue();
        assertThat(q.limit()).isEqualTo(300);
    }

    @Test
    void activeMaison_isUnlimited() {
        usage(10_000);
        subscription(PlanTier.ATELIER_MAISON, "active");

        QuotaService.Quota q = quotaService.forUser(user);

        assertThat(q.unlimited()).isTrue();
        assertThat(q.limit()).isNull();
        assertThat(q.canCreate()).isTrue();
    }

    @Test
    void activeNonGrantingTier_isPlanNotEligible() {
        usage(0);
        subscription(PlanTier.ATELIER_PLUS, "active");

        QuotaService.Quota q = quotaService.forUser(user);

        assertThat(q.hasActiveSubscription()).isFalse();
        assertThat(q.canCreate()).isFalse();
        assertThat(q.reason()).isEqualTo(QuotaService.REASON_PLAN_NOT_ELIGIBLE);
    }

    @Test
    void assertCanCreate_throwsWhenNoSubscription() {
        usage(0);
        noSubscription();

        assertThatThrownBy(() -> quotaService.assertCanCreate(user))
                .isInstanceOf(SubscriptionRequiredException.class);
    }

    @Test
    void assertCanCreate_throwsWhenQuotaExceeded() {
        usage(50);
        subscription(PlanTier.ATELIER_SOLO, "active");

        assertThatThrownBy(() -> quotaService.assertCanCreate(user))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void assertCanCreate_passesWhenUnderLimit() {
        usage(49);
        subscription(PlanTier.ATELIER_SOLO, "active");

        assertThatCode(() -> quotaService.assertCanCreate(user)).doesNotThrowAnyException();
    }
}
