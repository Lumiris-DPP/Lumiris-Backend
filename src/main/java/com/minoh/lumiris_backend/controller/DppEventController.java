package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.DppEventRequest;
import com.minoh.lumiris_backend.dto.out.DppEventResponse;
import com.minoh.lumiris_backend.service.DppEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dpp-forms/{id}/events")
@RequiredArgsConstructor
public class DppEventController {

    private final DppEventService dppEventService;

    @PostMapping
    ResponseEntity<DppEventResponse> create(
            @PathVariable UUID id,
            @Valid @RequestBody DppEventRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dppEventService.create(id, request, principal.getUsername()));
    }

    @GetMapping
    ResponseEntity<List<DppEventResponse>> findAll(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(dppEventService.findAllByDppFormId(id, principal.getUsername()));
    }
}
