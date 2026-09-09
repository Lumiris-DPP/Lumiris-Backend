package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.RepairerReviewResponse;
import com.minoh.lumiris_backend.entity.RepairRequest;
import com.minoh.lumiris_backend.entity.RepairRequestStatus;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerReview;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.RepairerReviewRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairerReviewServiceTest {

    @Mock private RepairerReviewRepository reviewRepo;
    @Mock private RepairerProfileRepository repairerRepo;
    @Mock private RepairRequestRepository requestRepo;
    @Mock private UserRepository userRepo;

    private RepairerReviewService service;

    private User consumer;
    private RepairRequest request;
    private final UUID requestId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RepairerReviewService(reviewRepo, repairerRepo, requestRepo, userRepo);

        consumer = new User();
        consumer.setId(UUID.randomUUID());
        consumer.setName("Camille D.");

        RepairerProfile repairer = new RepairerProfile();
        repairer.setId(UUID.randomUUID());

        request = new RepairRequest();
        request.setConsumerUser(consumer);
        request.setRepairerProfile(repairer);
        request.setStatus(RepairRequestStatus.COMPLETED);

        lenient().when(userRepo.findByEmail("camille@x.fr")).thenReturn(Optional.of(consumer));
        lenient().when(requestRepo.findById(requestId)).thenReturn(Optional.of(request));
        lenient().when(reviewRepo.existsByRepairRequestId(requestId)).thenReturn(false);
        lenient().when(reviewRepo.save(any())).thenAnswer(i -> {
            RepairerReview r = i.getArgument(0);
            r.setCreatedAt(java.time.Instant.now());
            return r;
        });
    }

    @Test
    void submitForRequest_createsAVerifiedReviewFromTheConsumersName() {
        RepairerReviewResponse res = service.submitForRequest("camille@x.fr", requestId, 5, "Impeccable");

        assertThat(res.verified()).isTrue();
        assertThat(res.reviewerName()).isEqualTo("Camille D.");
        assertThat(res.rating()).isEqualTo(5);
    }

    @Test
    void submitForRequest_rejectsARatingOutOfRange() {
        assertThatThrownBy(() -> service.submitForRequest("camille@x.fr", requestId, 6, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitForRequest_rejectsARequestOwnedBySomeoneElse() {
        request.setConsumerUser(new User() {{ setId(UUID.randomUUID()); }});

        assertThatThrownBy(() -> service.submitForRequest("camille@x.fr", requestId, 4, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitForRequest_rejectsAnInterventionNotYetCompleted() {
        request.setStatus(RepairRequestStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.submitForRequest("camille@x.fr", requestId, 4, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submitForRequest_rejectsASecondReviewForTheSameIntervention() {
        when(reviewRepo.existsByRepairRequestId(requestId)).thenReturn(true);

        assertThatThrownBy(() -> service.submitForRequest("camille@x.fr", requestId, 4, null))
                .isInstanceOf(ConflictException.class);
    }
}
