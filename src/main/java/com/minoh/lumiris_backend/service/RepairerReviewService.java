package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.RepairerReviewRequest;
import com.minoh.lumiris_backend.dto.out.RepairerReviewResponse;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerReview;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.RepairerReviewRepository;
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

    @Transactional(readOnly = true)
    public List<RepairerReviewResponse> findByRepairerId(UUID repairerId) {
        RepairerProfile profile = findProfile(repairerId);
        return reviewRepo.findByRepairerProfileOrderByCreatedAtDesc(profile).stream()
                .map(this::toResponse)
                .toList();
    }

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

    private RepairerProfile findProfile(UUID id) {
        return repairerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Retoucheur introuvable : " + id));
    }

    private RepairerReviewResponse toResponse(RepairerReview r) {
        return new RepairerReviewResponse(r.getId(), r.getRating(), r.getComment(), r.getReviewerName(), r.getCreatedAt());
    }
}
