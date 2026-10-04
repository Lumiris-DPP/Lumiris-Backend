package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DppOwnershipGuardTest {

    private static final String OWNER_EMAIL = "artisan@test.com";
    private static final String OTHER_EMAIL = "autre-artisan@test.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DppFormRepository dppFormRepository;

    @InjectMocks
    private DppOwnershipGuard guard;

    private User owner;
    private DppForm form;

    @BeforeEach
    void setUp() {
        owner = user(OWNER_EMAIL);
        form = new DppForm();
        form.setId(UUID.randomUUID());
        form.setUser(owner);
        form.setStatus(DppStatus.DRAFT);
    }

    @Test
    void loadOwned_shouldReturnTheFormToItsOwner() {
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(dppFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertThat(guard.loadOwned(form.getId(), OWNER_EMAIL)).isSameAs(form);
    }

    // 404 et non 403 : un autre artisan ne doit pas pouvoir distinguer « inexistant » de « pas à moi ».
    @Test
    void loadOwned_shouldAnswer404_toAnotherArtisan() {
        when(userRepository.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(user(OTHER_EMAIL)));
        when(dppFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertThatThrownBy(() -> guard.loadOwned(form.getId(), OTHER_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("DPP not found");
    }

    @Test
    void loadOwnedDraft_shouldRejectAPublishedPassport() {
        form.setStatus(DppStatus.VALID);
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(dppFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertThatThrownBy(() -> guard.loadOwnedDraft(form.getId(), OWNER_EMAIL))
                .isInstanceOf(ConflictException.class);
    }

    private static User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        return user;
    }
}
