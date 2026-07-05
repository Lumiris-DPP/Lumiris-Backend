package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.security.JwtService;
import com.minoh.lumiris_backend.dto.in.LoginRequest;
import com.minoh.lumiris_backend.dto.in.RefreshRequest;
import com.minoh.lumiris_backend.dto.in.RegisterRequest;
import com.minoh.lumiris_backend.dto.out.AuthResponse;
import com.minoh.lumiris_backend.dto.out.UserResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.RefreshToken;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.RefreshTokenRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ArtisanProfileRepository artisanProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ArtisanOnboardingService artisanOnboardingService;

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.password())
        );
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setLastSeenAt(Instant.now());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setName(req.name().trim());
        user.setRole(req.role());
        user.setVerified(false);
        userRepository.save(user);

        if (req.role() == UserRole.ARTISAN) {
            ArtisanProfile profile = new ArtisanProfile();
            profile.setUser(user);
            profile.setDisplayName(req.name().trim());
            profile.setTier("Solo");
            profile.setPassportLimit(50);
            profile.setJoinedAt(Instant.now());
            artisanProfileRepository.save(profile);
            user.setArtisanProfile(profile);

            if (req.siret() != null && !req.siret().isBlank()) {
                artisanOnboardingService.verifySiretOnSignup(user, req.siret());
            }
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String hash = hashToken(req.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildAuthResponse(stored.getUser());
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = issueRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken, buildUserResponse(user));
    }

    private String issueRefreshToken(User user) {
        String rawToken = jwtService.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshExpiration()));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return buildUserResponse(user);
    }

    private UserResponse buildUserResponse(User user) {
        String artisanId = null;
        if (user.getRole() == UserRole.ARTISAN && user.getArtisanProfile() != null) {
            artisanId = user.getArtisanProfile().getId().toString();
        }
        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getRole(),
                user.getName(),
                user.getAvatarUrl(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                user.getLastSeenAt() != null ? user.getLastSeenAt().toString() : null,
                artisanId
        );
    }
}
