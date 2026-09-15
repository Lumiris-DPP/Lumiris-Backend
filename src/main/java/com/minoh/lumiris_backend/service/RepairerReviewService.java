package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.RepairerReviewRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairerReviewService {

    private final RepairerReviewRepository reviewRepo;
    private final RepairerProfileRepository repairerRepo;
    private final RepairRequestRepository requestRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public List<RepairerReviewResponse> findByRepairerId(UUID repairerId) {
        RepairerProfile profile = findProfile(repairerId);
        return reviewRepo.findByRepairerProfileOrderByCreatedAtDesc(profile).stream()
                .map(this::toResponse)
                .toList();
    }

    // Ancien flux : avis anonyme, non rattaché à une intervention.
    // @deprecated à retirer une fois le front migré vers submitForRequest().
    @Deprecated
    @Transactional
    public RepairerReviewResponse create(UUID repairerId, RepairerReviewRequest request) {
        RepairerProfile profile = findProfile(repairerId);

        RepairerReview review = new RepairerReview();
        review.setRepairerProfile(profile);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setReviewerName(request.reviewerName());

        return toResponse(reviewRepo.save(review));
    }

    // Avis vérifié : réservé au client d'une intervention TERMINÉE, un seul par demande.
    @Transactional
    public RepairerReviewResponse submitForRequest(String consumerEmail, UUID requestId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("La note doit être comprise entre 1 et 5.");
        }
        User consumer = userRepo.findByEmail(consumerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        RepairRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable : " + requestId));

        if (!request.getConsumerUser().getId().equals(consumer.getId())) {
            throw new ResourceNotFoundException("Demande introuvable : " + requestId);
        }
        if (request.getStatus() != RepairRequestStatus.COMPLETED) {
            throw new ConflictException("Vous ne pouvez noter qu'une intervention terminée.");
        }
        if (reviewRepo.existsByRepairRequestId(requestId)) {
            throw new ConflictException("Cette intervention a déjà été notée.");
        }

        RepairerReview review = new RepairerReview();
        review.setRepairerProfile(request.getRepairerProfile());
        review.setRating(rating);
        review.setComment(comment);
        review.setReviewerName(consumer.getName());
        review.setRepairRequestId(requestId);

        return toResponse(reviewRepo.save(review));
    }

    private RepairerProfile findProfile(UUID id) {
        return repairerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Retoucheur introuvable : " + id));
    }

    private RepairerReviewResponse toResponse(RepairerReview r) {
        return new RepairerReviewResponse(
                r.getId(), r.getRating(), r.getComment(), r.getReviewerName(),
                r.getRepairRequestId() != null, r.getCreatedAt());
    }
}
