package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.ArtisanStatusUpdateRequest;
import com.minoh.lumiris_backend.dto.out.ArtisanProfileResponse;
import com.minoh.lumiris_backend.service.ArtisanOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/artisans")
@RequiredArgsConstructor
public class AdminArtisanController {

    private final ArtisanOnboardingService onboardingService;

    @GetMapping
    ResponseEntity<List<ArtisanProfileResponse>> listPending() {
        return ResponseEntity.ok(onboardingService.findPending());
    }

    @PatchMapping("/{id}/verify")
    ResponseEntity<ArtisanProfileResponse> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(onboardingService.verify(id));
    }

    @PatchMapping("/{id}/reject")
    ResponseEntity<ArtisanProfileResponse> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) ArtisanStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(onboardingService.reject(id, request != null ? request : new ArtisanStatusUpdateRequest(null)));
    }
}
