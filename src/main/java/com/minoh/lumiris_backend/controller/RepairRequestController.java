package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.RepairAppointmentRequest;
import com.minoh.lumiris_backend.dto.in.RepairMessageRequest;
import com.minoh.lumiris_backend.dto.in.RepairRequestCreateRequest;
import com.minoh.lumiris_backend.dto.out.RepairMessageResponse;
import com.minoh.lumiris_backend.dto.out.RepairRequestResponse;
import com.minoh.lumiris_backend.service.RepairMessageService;
import com.minoh.lumiris_backend.service.RepairRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Côté client VISION : création, décision sur le devis, annulation, messagerie.
// Les actions côté retoucheur (devis, démarrage, clôture) vivent dans RepairerController.
@RestController
@RequestMapping("/api/repair-requests")
@RequiredArgsConstructor
public class RepairRequestController {

    private final RepairRequestService requestService;
    private final RepairMessageService messageService;

    @PostMapping
    ResponseEntity<RepairRequestResponse> create(
            @Valid @RequestBody RepairRequestCreateRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.create(principal.getUsername(), request));
    }

    @GetMapping("/mine")
    ResponseEntity<List<RepairRequestResponse>> mine(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(requestService.findForConsumer(principal.getUsername()));
    }

    @PostMapping("/{id}/accept-quote")
    ResponseEntity<RepairRequestResponse> acceptQuote(
            @PathVariable UUID id,
            @Valid @RequestBody RepairAppointmentRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(requestService.acceptQuote(principal.getUsername(), id, request));
    }

    @PostMapping("/{id}/refuse-quote")
    ResponseEntity<RepairRequestResponse> refuseQuote(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(requestService.refuseQuote(principal.getUsername(), id));
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<RepairRequestResponse> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(requestService.cancel(principal.getUsername(), id));
    }

    @GetMapping("/{id}/messages")
    ResponseEntity<List<RepairMessageResponse>> messages(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(messageService.findByRequest(principal.getUsername(), id));
    }

    @PostMapping("/{id}/messages")
    ResponseEntity<RepairMessageResponse> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody RepairMessageRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.send(principal.getUsername(), id, request));
    }
}
