package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.dto.out.IrisScoreResponse;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.service.scoring.IrisScoreCalculator;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
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

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private IrisScoreRepository irisScoreRepository;

    @Mock
    private IrisScoreCalculator irisScoreCalculator;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private StorageService storageService;

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
        lenient().when(dppFormRepository.save(any())).thenAnswer(inv -> {
            DppForm f = inv.getArgument(0);
            if (f.getId() == null) f.setId(UUID.randomUUID());
            return f;
        });
        lenient().when(irisScoreCalculator.compute(any())).thenReturn(
                new IrisScoreResponse(32, "D",
                        new IrisScoreResponse.Breakdown(18, 10, 0, 4),
                        IrisScoreResponse.FIXED_WEIGHTS,
                        List.of())
        );
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void create_shouldPersistAndReturnId() {
        DppFormRequest request = new DppFormRequest(
                "Pull Merino", "Un pull doux", "top", "FR",
                List.of("S", "M"), List.of("Écru"),
                List.of(), List.of(), null,
                "2026-01-01", "LOT-001", null, "SKU-001", true,
                30, "2 ans", true, "Rapporter en boutique"
        );

        DppFormCreatedResponse response = service.create(request, Collections.emptyMap(), USER_EMAIL);

        verify(dppFormRepository).save(any());
        assertThat(response.id()).isNotNull();
    }

    @Test
    void create_shouldPersistAllFields() {
        DppFormRequest request = new DppFormRequest(
                "Veste Lin", "Description", "outerwear", "IT",
                List.of("M", "L", "XL"), List.of("Beige", "Noir"),
                List.of(), List.of("wash-30"), null,
                "2026-03-15", "LOT-002", "1234567890123", "SKU-002", false,
                null, null, false, null
        );

        DppFormCreatedResponse response = service.create(request, Collections.emptyMap(), USER_EMAIL);

        verify(dppFormRepository).save(any());
        assertThat(response.id()).isNotNull();
    }

    @Test
    void create_shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(null, Collections.emptyMap(), "unknown@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
