package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.ArtisanRegisterRequest;
import com.minoh.lumiris_backend.dto.out.ArtisanProfileResponse;
import com.minoh.lumiris_backend.service.ArtisanOnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artisans")
@RequiredArgsConstructor
public class ArtisanController {

    private final ArtisanOnboardingService onboardingService;

    @GetMapping("/me")
    ResponseEntity<ArtisanProfileResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(onboardingService.findByUserEmail(principal.getUsername()));
    }

    @PostMapping("/register")
    ResponseEntity<ArtisanProfileResponse> register(
            @Valid @RequestBody ArtisanRegisterRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.register(principal.getUsername(), request));
    }

    @PostMapping("/sign-declaration")
    ResponseEntity<ArtisanProfileResponse> signDeclaration(
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest
    ) {
        String ip = resolveClientIp(httpRequest);
        return ResponseEntity.ok(onboardingService.signDeclaration(principal.getUsername(), ip));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
