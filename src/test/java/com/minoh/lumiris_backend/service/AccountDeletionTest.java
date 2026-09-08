package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.config.security.JwtService;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.repository.RefreshTokenRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("bob@example.com");
        user.setName("Bob");
        user.setRole(UserRole.CONSUMER);
        user.setVerified(true);
        lenient().when(userRepository.getByEmail("bob@example.com")).thenReturn(user);
        lenient().when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("scrambled");
        lenient().when(jwtService.generateRefreshToken()).thenReturn("random");
    }

    @Test
    void deleteAccount_softDeletes_keepingIdentityDataForTheGracePeriod() {
        authService.deleteAccount("bob@example.com");

        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getAnonymizedAt()).isNull();
        assertThat(user.getEmail()).isEqualTo("bob@example.com"); // still recoverable
        verify(refreshTokenRepository).deleteByUser_Id(user.getId());
    }

    @Test
    void deleteAccount_isIdempotent() {
        authService.deleteAccount("bob@example.com");
        var firstDeletedAt = user.getDeletedAt();
        authService.deleteAccount("bob@example.com");

        assertThat(user.getDeletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    void anonymizeAccount_neutralisesIdentityAndBlocksLogin() {
        authService.anonymizeAccount(user);

        assertThat(user.getAnonymizedAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("deleted-" + user.getId() + "@deleted.lumiris.invalid");
        assertThat(user.getName()).isNull();
        assertThat(user.isVerified()).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("scrambled");
    }

    @Test
    void anonymizeAccount_isIdempotent() {
        authService.anonymizeAccount(user);
        String anonymisedEmail = user.getEmail();
        authService.anonymizeAccount(user);

        assertThat(user.getEmail()).isEqualTo(anonymisedEmail);
    }
}
