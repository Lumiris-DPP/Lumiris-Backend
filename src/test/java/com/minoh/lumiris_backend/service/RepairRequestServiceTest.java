package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.domain.PlanTier;
import com.minoh.lumiris_backend.dto.in.RepairAppointmentRequest;
import com.minoh.lumiris_backend.dto.in.RepairQuoteRequest;
import com.minoh.lumiris_backend.dto.out.RepairRequestResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserSubscription;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairRequestServiceTest {

    @Mock private RepairRequestRepository requestRepo;
    @Mock private RepairerProfileRepository repairerRepo;
    @Mock private DppFormRepository dppFormRepo;
    @Mock private UserRepository userRepo;
    @Mock private SubscriptionRepository subscriptionRepo;
    @Mock private MailService mailService;
    @Mock private AffiliateTrackingService affiliateTrackingService;
    @Mock private com.minoh.lumiris_backend.service.stripe.RepairRequestRefundService refundService;

    @InjectMocks
    private RepairRequestService service;

    private User consumer;
    private User repairerUser;
    private RepairerProfile repairerProfile;
    private DppForm dppForm;

    @BeforeEach
    void setUp() {
        consumer = new User();
        consumer.setId(UUID.randomUUID());
        consumer.setEmail("client@lumiris.com");

        repairerUser = new User();
        repairerUser.setId(UUID.randomUUID());
        repairerUser.setEmail("repairer@lumiris.com");

        repairerProfile = new RepairerProfile();
        repairerProfile.setId(UUID.randomUUID());
        repairerProfile.setUser(repairerUser);
        repairerProfile.setDisplayName("Atelier Test");

        dppForm = new DppForm();
        dppForm.setId(UUID.randomUUID());
        dppForm.setPublicCode("ABC12345");
        dppForm.setProductName("Veste en cuir");

        lenient().when(userRepo.findByEmail("repairer@lumiris.com")).thenReturn(Optional.of(repairerUser));
        lenient().when(userRepo.findByEmail("client@lumiris.com")).thenReturn(Optional.of(consumer));
        lenient().when(repairerRepo.findByUser(repairerUser)).thenReturn(Optional.of(repairerProfile));
        lenient().when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RepairRequest newRequest(RepairRequestStatus status) {
        RepairRequest r = new RepairRequest();
        r.setId(UUID.randomUUID());
        r.setRepairerProfile(repairerProfile);
        r.setConsumerUser(consumer);
        r.setDppForm(dppForm);
        r.setStatus(status);
        return r;
    }

    @Test
    void submitQuote_movesFromPendingToDraft() {
        RepairRequest request = newRequest(RepairRequestStatus.PENDING);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        RepairRequestResponse response = service.submitQuote(
                "repairer@lumiris.com", request.getId(), new RepairQuoteRequest(5000, "Recoudre la doublure"));

        assertThat(response.status()).isEqualTo(RepairRequestStatus.DRAFT);
        assertThat(response.quoteAmountCents()).isEqualTo(5000);
    }

    @Test
    void submitQuote_rejectedWhenNotPending() {
        RepairRequest request = newRequest(RepairRequestStatus.ACCEPTED);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() ->
                service.submitQuote("repairer@lumiris.com", request.getId(), new RepairQuoteRequest(5000, "x")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptQuote_locksAppointmentAndTracksAffiliate_whenRepairerHasActiveLocalSubscription() {
        RepairRequest request = newRequest(RepairRequestStatus.DRAFT);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        UserSubscription sub = new UserSubscription();
        sub.setPlanTier(PlanTier.LOCAL);
        sub.setStatus("active");
        when(subscriptionRepo.findByUserId(repairerUser.getId())).thenReturn(Optional.of(sub));

        Instant appointment = Instant.now().plusSeconds(3600);
        RepairRequestResponse response = service.acceptQuote(
                "client@lumiris.com", request.getId(), new RepairAppointmentRequest(appointment));

        assertThat(response.status()).isEqualTo(RepairRequestStatus.ACCEPTED);
        assertThat(response.appointmentAt()).isEqualTo(appointment);
        verify(affiliateTrackingService).track(any());
    }

    @Test
    void acceptQuote_doesNotTrackAffiliate_whenRepairerHasNoSubscription() {
        RepairRequest request = newRequest(RepairRequestStatus.DRAFT);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));
        when(subscriptionRepo.findByUserId(repairerUser.getId())).thenReturn(Optional.empty());

        service.acceptQuote("client@lumiris.com", request.getId(), new RepairAppointmentRequest(Instant.now().plusSeconds(60)));

        verify(affiliateTrackingService, never()).track(any());
    }

    @Test
    void refuseQuote_completesRequestAndNotifiesRepairer() {
        RepairRequest request = newRequest(RepairRequestStatus.DRAFT);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        RepairRequestResponse response = service.refuseQuote("client@lumiris.com", request.getId());

        assertThat(response.status()).isEqualTo(RepairRequestStatus.COMPLETED);
        verify(mailService).sendRepairRequestRefused(repairerUser.getEmail(), repairerUser.getName(), dppForm.getProductName());
    }

    @Test
    void statusNeverGoesBackwards_onceInProgress() {
        RepairRequest request = newRequest(RepairRequestStatus.IN_PROGRESS);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() ->
                service.submitQuote("repairer@lumiris.com", request.getId(), new RepairQuoteRequest(1000, "x")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_blockedWhenAlreadyCompleted() {
        RepairRequest request = newRequest(RepairRequestStatus.COMPLETED);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel("client@lumiris.com", request.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_allowedFromAnyNonCompletedStatus() {
        RepairRequest request = newRequest(RepairRequestStatus.ACCEPTED);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        RepairRequestResponse response = service.cancel("client@lumiris.com", request.getId());

        assertThat(response.status()).isEqualTo(RepairRequestStatus.COMPLETED);
    }

    @Test
    void cancel_refundsWhenTheQuoteWasPaidAndWorkNotStarted() {
        RepairRequest request = newRequest(RepairRequestStatus.ACCEPTED);
        request.setPaidAt(Instant.now());
        request.setStripePaymentIntentId("pi_123");
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        service.cancel("client@lumiris.com", request.getId());

        verify(refundService).refundQuotePayment(request);
    }

    @Test
    void cancel_doesNotRefundOnceWorkHasStarted() {
        RepairRequest request = newRequest(RepairRequestStatus.IN_PROGRESS);
        request.setPaidAt(Instant.now());
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        service.cancel("client@lumiris.com", request.getId());

        verify(refundService, never()).refundQuotePayment(any());
    }

    @Test
    void requirePayableQuote_rejectsAQuoteWithoutAmount() {
        RepairRequest request = newRequest(RepairRequestStatus.DRAFT);
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.requirePayableQuote("client@lumiris.com", request.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requirePayableQuote_rejectsAnAlreadyPaidQuote() {
        RepairRequest request = newRequest(RepairRequestStatus.DRAFT);
        request.setQuoteAmountCents(5000L);
        request.setPaidAt(Instant.now());
        when(requestRepo.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.requirePayableQuote("client@lumiris.com", request.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmQuotePaid_isIdempotentAndAcceptsTheQuote() {
        RepairRequest request = newRequest(RepairRequestStatus.DRAFT);
        request.setQuoteAmountCents(5000L);
        request.setStripePaymentIntentId("pi_abc");
        when(requestRepo.findByStripePaymentIntentId("pi_abc")).thenReturn(Optional.of(request));
        when(subscriptionRepo.findByUserId(repairerUser.getId())).thenReturn(Optional.empty());

        service.confirmQuotePaid("pi_abc");
        Instant firstPaidAt = request.getPaidAt();
        service.confirmQuotePaid("pi_abc");

        assertThat(request.getStatus()).isEqualTo(RepairRequestStatus.ACCEPTED);
        assertThat(request.getPaidAt()).isEqualTo(firstPaidAt);
    }
}
