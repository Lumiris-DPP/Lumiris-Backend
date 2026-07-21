package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.RepairerReviewRequest;
import com.minoh.lumiris_backend.dto.out.RepairerPublicProfileResponse;
import com.minoh.lumiris_backend.dto.out.RepairerReviewResponse;
import com.minoh.lumiris_backend.dto.out.RepairerSearchResult;
import com.minoh.lumiris_backend.service.RepairerOnboardingService;
import com.minoh.lumiris_backend.service.RepairerReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/repairers")
@RequiredArgsConstructor
public class PublicRepairerController {

    private final RepairerOnboardingService onboardingService;
    private final RepairerReviewService reviewService;

    @GetMapping("/{id}")
    ResponseEntity<RepairerPublicProfileResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(onboardingService.findPublicById(id));
    }

    @GetMapping("/search")
    ResponseEntity<List<RepairerSearchResult>> search(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) Double radiusKm
    ) {
        return ResponseEntity.ok(onboardingService.search(lat, lng, specialty, radiusKm));
    }

    @GetMapping("/{id}/reviews")
    ResponseEntity<List<RepairerReviewResponse>> reviews(@PathVariable UUID id) {
        return ResponseEntity.ok(reviewService.findByRepairerId(id));
    }

    @PostMapping("/{id}/reviews")
    ResponseEntity<RepairerReviewResponse> addReview(
            @PathVariable UUID id,
            @Valid @RequestBody RepairerReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(id, request));
    }
}
