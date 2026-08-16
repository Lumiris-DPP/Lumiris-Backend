package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.ArtisanPauseRequest;
import com.minoh.lumiris_backend.dto.in.ArtisanRegisterRequest;
import com.minoh.lumiris_backend.dto.in.ArtisanVitrineUpdateRequest;
import com.minoh.lumiris_backend.dto.out.ArtisanPhotoResponse;
import com.minoh.lumiris_backend.dto.out.ArtisanProfileResponse;
import com.minoh.lumiris_backend.service.ArtisanOnboardingService;
import com.minoh.lumiris_backend.service.ArtisanVitrineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/artisans")
@RequiredArgsConstructor
public class ArtisanController {

    private final ArtisanOnboardingService onboardingService;
    private final ArtisanVitrineService vitrineService;

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

    @PutMapping("/me/profile")
    ResponseEntity<ArtisanProfileResponse> updateProfile(
            @RequestBody ArtisanVitrineUpdateRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(vitrineService.updateProfile(principal.getUsername(), request));
    }

    @PostMapping(value = "/me/photos", consumes = "multipart/form-data")
    ResponseEntity<ArtisanPhotoResponse> addPhoto(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vitrineService.addPhoto(principal.getUsername(), file));
    }

    @DeleteMapping("/me/photos/{photoId}")
    ResponseEntity<Void> removePhoto(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        vitrineService.removePhoto(principal.getUsername(), photoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/publish")
    ResponseEntity<ArtisanProfileResponse> publish(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(vitrineService.publish(principal.getUsername()));
    }

    @PutMapping("/me/pause")
    ResponseEntity<ArtisanProfileResponse> pause(
            @Valid @RequestBody ArtisanPauseRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(vitrineService.pause(principal.getUsername(), request.until()));
    }

    @DeleteMapping("/me/pause")
    ResponseEntity<ArtisanProfileResponse> resume(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(vitrineService.resume(principal.getUsername()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
