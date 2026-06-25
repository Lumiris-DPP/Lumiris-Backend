package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DppFormServiceTest {

    @Mock
    private DppFormRepository dppFormRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private DppFormMapper dppFormMapper;

    @InjectMocks
    private DppFormService service;

    private static final String USER_EMAIL = "artisan@test.com";
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(USER_EMAIL);

        lenient().when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
        lenient().when(dppFormRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_shouldPersistAndReturnResponse() {
        DppFormRequest request = new DppFormRequest(
                "Pull Merino", "Un pull doux", "top", "FR",
                List.of("S", "M"), List.of("Écru"), null,
                List.of(), List.of(), List.of(),
                "2026-01-01", "LOT-001", null, "SKU-001", true,
                30, "2 ans", true, "Rapporter en boutique"
        );

        DppFormResponse response = service.create(request, USER_EMAIL);

        verify(dppFormRepository).save(any());
        assertThat(response.productName()).isEqualTo("Pull Merino");
        assertThat(response.productCategory()).isEqualTo("top");
        assertThat(response.reachCompliant()).isTrue();
        assertThat(response.recycledPct()).isEqualTo(30);
    }

    @Test
    void create_shouldHandleNullRequest() {
        DppFormResponse response = service.create(null, USER_EMAIL);

        verify(dppFormRepository).save(any());
        assertThat(response.productName()).isNull();
    }

    @Test
    void create_shouldPersistAllFields() {
        DppFormRequest request = new DppFormRequest(
                "Veste Lin", "Description", "outerwear", "IT",
                List.of("M", "L", "XL"), List.of("Beige", "Noir"), "photo.jpg",
                List.of(), List.of("wash-30"), List.of(),
                "2026-03-15", "LOT-002", "1234567890123", "SKU-002", false,
                null, null, false, null
        );

        DppFormResponse response = service.create(request, USER_EMAIL);

        assertThat(response.originCountry()).isEqualTo("IT");
        assertThat(response.availableSizes()).containsExactly("M", "L", "XL");
        assertThat(response.careInstructions()).containsExactly("wash-30");
        assertThat(response.gtin()).isEqualTo("1234567890123");
        assertThat(response.reachCompliant()).isFalse();
        assertThat(response.isRepairable()).isFalse();
    }

    @Test
    void create_shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(null, "unknown@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
