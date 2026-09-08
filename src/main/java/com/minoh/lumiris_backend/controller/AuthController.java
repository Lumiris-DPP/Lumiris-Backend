package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.LoginRequest;
import com.minoh.lumiris_backend.dto.in.RefreshRequest;
import com.minoh.lumiris_backend.dto.in.RegisterRequest;
import com.minoh.lumiris_backend.dto.out.AuthResponse;
import com.minoh.lumiris_backend.dto.out.UserResponse;
import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.service.AccountDataExportService;
import com.minoh.lumiris_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountDataExportService accountDataExportService;

    @PostMapping("/sign-in")
    ResponseEntity<AuthResponse> signIn(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/sign-up")
    ResponseEntity<AuthResponse> signUp(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @GetMapping("/me")
    ResponseEntity<UserResponse> me(@CurrentUserEmail String email) {
        return ResponseEntity.ok(authService.me(email));
    }

    // RGPD — portabilité (art. 20) : archive ZIP des données personnelles du compte courant.
    @GetMapping(value = "/me/export", produces = "application/zip")
    ResponseEntity<byte[]> exportMe(@CurrentUserEmail String email) {
        byte[] archive = accountDataExportService.export(email);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"lumiris-export-" + LocalDate.now() + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(archive);
    }

    // RGPD — effacement (art. 17) : suppression douce immédiate du compte courant, anonymisation
    // définitive après 30 j (AccountPurgeScheduler). 204. Le client doit ensuite purger sa session.
    @DeleteMapping("/me")
    ResponseEntity<Void> deleteMe(@CurrentUserEmail String email) {
        authService.deleteAccount(email);
        return ResponseEntity.noContent().build();
    }
}
