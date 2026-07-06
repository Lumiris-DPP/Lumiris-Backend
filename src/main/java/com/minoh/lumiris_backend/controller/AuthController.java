package com.minoh.lumiris_backend.controller;

import com.minoh.lumiris_backend.dto.in.LoginRequest;
import com.minoh.lumiris_backend.dto.in.RegisterRequest;
import com.minoh.lumiris_backend.dto.out.AuthResponse;
import com.minoh.lumiris_backend.dto.out.UserResponse;
import com.minoh.lumiris_backend.config.security.CurrentUserEmail;
import com.minoh.lumiris_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/me")
    ResponseEntity<UserResponse> me(@CurrentUserEmail String email) {
        return ResponseEntity.ok(authService.me(email));
    }
}
