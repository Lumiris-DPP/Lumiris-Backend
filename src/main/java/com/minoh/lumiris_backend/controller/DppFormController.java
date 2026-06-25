package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppFormSummaryResponse;
import com.minoh.lumiris_backend.service.DppFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dpp-forms")
@RequiredArgsConstructor
public class DppFormController {

    private final DppFormService dppFormService;

    @PostMapping
    ResponseEntity<DppFormResponse> create(
            @RequestBody(required = false) DppFormRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dppFormService.create(request, principal.getUsername()));
    }

    @GetMapping
    ResponseEntity<List<DppFormSummaryResponse>> findAll(
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(dppFormService.findAllByUser(principal.getUsername()));
    }

    @GetMapping("/{id}")
    ResponseEntity<DppFormResponse> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(dppFormService.findById(id, principal.getUsername()));
    }
}
