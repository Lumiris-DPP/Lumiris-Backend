package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.KybDetailsRequest;
import com.minoh.lumiris_backend.dto.in.RepairMessageRequest;
import com.minoh.lumiris_backend.dto.in.RepairQuoteRequest;
import com.minoh.lumiris_backend.dto.in.RepairerProfileUpdateRequest;
import com.minoh.lumiris_backend.dto.in.RepairerRegisterRequest;
import com.minoh.lumiris_backend.dto.out.RepairMessageResponse;
import com.minoh.lumiris_backend.dto.out.RepairRequestResponse;
import com.minoh.lumiris_backend.dto.out.RepairerProfileResponse;
import com.minoh.lumiris_backend.entity.KybDocumentLabel;
import com.minoh.lumiris_backend.service.RepairMessageService;
import com.minoh.lumiris_backend.service.RepairRequestService;
import com.minoh.lumiris_backend.service.RepairerOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repairers")
@RequiredArgsConstructor
public class RepairerController {

    private final RepairerOnboardingService onboardingService;
    private final RepairRequestService requestService;
    private final RepairMessageService messageService;

    @GetMapping("/me")
    ResponseEntity<RepairerProfileResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(onboardingService.findByUserEmail(principal.getUsername()));
    }

    @PostMapping("/register")
    ResponseEntity<RepairerProfileResponse> register(
            @Valid @RequestBody RepairerRegisterRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.register(principal.getUsername(), request));
    }

    @PutMapping("/me/kyb")
    ResponseEntity<RepairerProfileResponse> submitKyb(
            @Valid @RequestBody KybDetailsRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(onboardingService.submitKyb(principal.getUsername(), request));
    }

    @PostMapping(value = "/me/kyb/documents/{label}", consumes = "multipart/form-data")
    ResponseEntity<RepairerProfileResponse> uploadKybDocument(
            @PathVariable KybDocumentLabel label,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(onboardingService.uploadKybDocument(principal.getUsername(), label, file));
    }

    @PutMapping("/me/profile")
    ResponseEntity<RepairerProfileResponse> updateProfile(
            @RequestBody RepairerProfileUpdateRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(onboardingService.updateProfile(principal.getUsername(), request));
    }

    @GetMapping("/me/requests")
    ResponseEntity<List<RepairRequestResponse>> myRequests(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(requestService.findForRepairer(principal.getUsername()));
    }

    @PostMapping("/me/requests/{id}/quote")
    ResponseEntity<RepairRequestResponse> submitQuote(
            @PathVariable UUID id,
            @Valid @RequestBody RepairQuoteRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(requestService.submitQuote(principal.getUsername(), id, request));
    }

    @PostMapping("/me/requests/{id}/start")
    ResponseEntity<RepairRequestResponse> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(requestService.start(principal.getUsername(), id));
    }

    @PostMapping("/me/requests/{id}/complete")
    ResponseEntity<RepairRequestResponse> complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(requestService.complete(principal.getUsername(), id));
    }

    @GetMapping("/me/requests/{id}/messages")
    ResponseEntity<List<RepairMessageResponse>> messages(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(messageService.findByRequest(principal.getUsername(), id));
    }

    @PostMapping("/me/requests/{id}/messages")
    ResponseEntity<RepairMessageResponse> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody RepairMessageRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.send(principal.getUsername(), id, request));
    }
}
