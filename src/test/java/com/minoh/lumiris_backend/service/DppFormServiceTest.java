package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.dto.out.DppFormResponse;
import com.minoh.lumiris_backend.dto.out.DppVerificationResponse;
import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
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
import com.minoh.lumiris_backend.util.DppHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Mock
    private DppHashUtil dppHashUtil;

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private QuotaService quotaService;

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
        lenient().when(dppHashUtil.generateDppHash(any(Map.class))).thenReturn("abc123fakehash");
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

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    void verify_shouldReturnPending_whenAnchorInProgress() {
        UUID id = UUID.randomUUID();
        DppForm form = formWithStatus(id, BlockchainAnchorStatus.PENDING, null);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(form));

        DppVerificationResponse response = service.verify(id);

        assertThat(response.verified()).isFalse();
        assertThat(response.anchorStatus()).isEqualTo(BlockchainAnchorStatus.PENDING);
        assertThat(response.blockchainHash()).isNull();
        assertThat(response.message()).contains("progress");
    }

    @Test
    void verify_shouldReturnFailed_whenAnchorFailed() {
        UUID id = UUID.randomUUID();
        DppForm form = formWithStatus(id, BlockchainAnchorStatus.FAILED, null);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(form));

        DppVerificationResponse response = service.verify(id);

        assertThat(response.verified()).isFalse();
        assertThat(response.anchorStatus()).isEqualTo(BlockchainAnchorStatus.FAILED);
        assertThat(response.blockchainHash()).isNull();
    }

    @Test
    void verify_shouldReturnTrue_whenHashesMatch() throws Exception {
        UUID id = UUID.randomUUID();
        String txHash = "0xabc123";
        String hash = "deadbeef1234";

        DppForm form = formWithStatus(id, BlockchainAnchorStatus.ANCHORED, txHash);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(form));
        when(blockchainService.retrieveHash(txHash)).thenReturn(hash);
        when(dppHashUtil.generateDppHash(any())).thenReturn(hash);

        DppVerificationResponse response = service.verify(id);

        assertThat(response.verified()).isTrue();
        assertThat(response.blockchainHash()).isEqualTo(hash);
        assertThat(response.recomputedHash()).isEqualTo(hash);
        assertThat(response.blockchainTxHash()).isEqualTo(txHash);
        assertThat(response.message()).isNull();
    }

    @Test
    void verify_shouldReturnFalse_whenDataWasTamperedInDb() throws Exception {
        UUID id = UUID.randomUUID();
        String txHash = "0xabc123";

        DppForm form = formWithStatus(id, BlockchainAnchorStatus.ANCHORED, txHash);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(form));
        when(blockchainService.retrieveHash(txHash)).thenReturn("original-hash");
        when(dppHashUtil.generateDppHash(any())).thenReturn("tampered-hash");

        DppVerificationResponse response = service.verify(id);

        assertThat(response.verified()).isFalse();
        assertThat(response.blockchainHash()).isEqualTo("original-hash");
        assertThat(response.recomputedHash()).isEqualTo("tampered-hash");
    }

    @Test
    void verify_shouldThrow_whenDppNotFound() {
        UUID id = UUID.randomUUID();
        when(dppFormRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verify_shouldReturnError_whenBlockchainNodeUnreachable() throws Exception {
        UUID id = UUID.randomUUID();
        String txHash = "0xabc123";

        DppForm form = formWithStatus(id, BlockchainAnchorStatus.ANCHORED, txHash);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(form));
        when(blockchainService.retrieveHash(txHash)).thenThrow(new java.io.IOException("Connection refused"));

        DppVerificationResponse response = service.verify(id);

        assertThat(response.verified()).isFalse();
        assertThat(response.anchorStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(response.message()).contains("Connection refused");
    }

    private static DppForm formWithStatus(UUID id, BlockchainAnchorStatus status, String txHash) {
        DppForm form = new DppForm();
        form.setBlockchainAnchorStatus(status);
        form.setBlockchainTxHash(txHash);
        return form;
    }
}
