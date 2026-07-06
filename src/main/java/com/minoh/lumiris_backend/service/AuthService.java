package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.security.JwtService;
import com.minoh.lumiris_backend.dto.in.LoginRequest;
import com.minoh.lumiris_backend.dto.in.RegisterRequest;
import com.minoh.lumiris_backend.dto.out.AuthResponse;
import com.minoh.lumiris_backend.dto.out.UserResponse;
import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.RoleNotAllowedException;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ArtisanProfileRepository artisanProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.password())
        );
        User user = userRepository.getByEmail(email);
        user.setLastSeenAt(Instant.now());

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, UserResponse.from(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (!req.role().isSelfAssignable()) {
            throw new RoleNotAllowedException("Ce rôle ne peut pas être choisi à l'inscription.");
        }
        String email = normalizeEmail(req.email());
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
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        User user = userRepository.getByEmail(email);
        return UserResponse.from(user);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
