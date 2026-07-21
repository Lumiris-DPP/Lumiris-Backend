package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.RejectionRequest;
import com.minoh.lumiris_backend.dto.out.RepairerProfileResponse;
import com.minoh.lumiris_backend.service.RepairerOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/repairers")
@RequiredArgsConstructor
public class AdminRepairerController {

    private final RepairerOnboardingService onboardingService;

    @GetMapping
    ResponseEntity<List<RepairerProfileResponse>> listPending() {
        return ResponseEntity.ok(onboardingService.findPending());
    }

    @GetMapping("/all")
    ResponseEntity<List<RepairerProfileResponse>> listAll() {
        return ResponseEntity.ok(onboardingService.findAll());
    }

    @PatchMapping("/{id}/verify")
    ResponseEntity<RepairerProfileResponse> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(onboardingService.verify(id));
    }

    @PatchMapping("/{id}/reject")
    ResponseEntity<RepairerProfileResponse> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectionRequest request
    ) {
        return ResponseEntity.ok(onboardingService.reject(id, request != null ? request : new RejectionRequest(null)));
    }

    @PatchMapping("/{id}/kyb-ongoing")
    ResponseEntity<RepairerProfileResponse> markOngoing(@PathVariable UUID id) {
        return ResponseEntity.ok(onboardingService.markKybOngoing(id));
    }

    @PatchMapping("/{id}/kyb-incomplete")
    ResponseEntity<RepairerProfileResponse> markIncomplete(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectionRequest request
    ) {
        String note = request != null ? request.reason() : null;
        return ResponseEntity.ok(onboardingService.markKybIncomplete(id, note));
    }
}
