package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.in.MaterialRequest;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.dto.out.DppVerificationResponse;
import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.entity.DppMaterial;
import com.minoh.lumiris_backend.entity.DppStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.exception.SubscriptionRequiredException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.dto.out.IrisScoreResponse;
import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.entity.DppAccessLevel;
import com.minoh.lumiris_backend.entity.DppDocumentVisibility;
import com.minoh.lumiris_backend.entity.DppFormDocument;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.dto.out.DppFormPublicResponse;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.DppCareInstructionRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.DppMaterialRepository;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    private final GeocodingService geocodingService = mock(GeocodingService.class);

    @Spy
    private DppFormMapper dppFormMapper = new DppFormMapper(geocodingService);

    @Mock
    private DppHashUtil dppHashUtil;

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private QuotaService quotaService;

    @Mock
    private DppMaterialRepository dppMaterialRepository;

    @Mock
    private DppCareInstructionRepository dppCareInstructionRepository;

    @Mock
    private ArtisanProfileRepository artisanProfileRepository;

    @Mock
    private AtelierStatsService atelierStatsService;

    @Mock
    private DppAccessTokenService accessTokenService;

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
                List.of(), List.of(), "none",
                "2026-01-01", "LOT-001", null, "SKU-001", true,
                30, "2 ans", 24, true, "Rapporter en boutique", 1
        );

        DppFormCreatedResponse response = service.create(request, Collections.emptyMap(), USER_EMAIL, false);

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
                null, null, null, false, null, 1
        );

        DppFormCreatedResponse response = service.create(request, Collections.emptyMap(), USER_EMAIL, false);

        verify(dppFormRepository).save(any());
        assertThat(response.id()).isNotNull();
    }

    @Test
    void create_shouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(null, Collections.emptyMap(), "unknown@test.com", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── billing gate ──────────────────────────────────────────────────────────

    @Test
    void create_draft_shouldNotRequireSubscription() {
        DppFormRequest request = new DppFormRequest(
                "Brouillon", null, null, null,
                null, null,
                List.of(), List.of(), null,
                null, null, null, null, false,
                null, null, null, false, null, 1
        );

        DppFormCreatedResponse response = service.create(request, Collections.emptyMap(), USER_EMAIL, true);

        verify(quotaService, never()).assertCanCreate(any());
        assertThat(response.id()).isNotNull();
    }

    @Test
    void create_nonDraft_shouldRequireSubscription() {
        doThrow(new SubscriptionRequiredException("Abonnement requis"))
                .when(quotaService).assertCanCreate(user);

        DppFormRequest request = new DppFormRequest(
                "Pull Merino", "Un pull doux", "top", "FR",
                List.of("S"), List.of("Écru"),
                List.of(), List.of(), "none",
                "2026-01-01", "LOT-001", null, "SKU-001", true,
                30, "2 ans", 24, true, "Rapporter en boutique", 1
        );

        assertThatThrownBy(() -> service.create(request, Collections.emptyMap(), USER_EMAIL, false))
                .isInstanceOf(SubscriptionRequiredException.class);
        verify(dppFormRepository, never()).save(any());
    }

    @Test
    void publish_shouldRequireSubscription() {
        UUID id = UUID.randomUUID();
        DppForm form = new DppForm();
        form.setId(id);
        form.setUser(user);
        form.setStatus(DppStatus.DRAFT);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(form));
        doThrow(new SubscriptionRequiredException("Abonnement requis"))
                .when(quotaService).assertCanCreate(user);

        assertThatThrownBy(() -> service.publish(id, USER_EMAIL))
                .isInstanceOf(SubscriptionRequiredException.class);
        verify(dppFormRepository, never()).save(any());
    }

    // ── géocodage des matières ────────────────────────────────────────────────

    private static DppFormRequest requestWithMaterial() {
        return new DppFormRequest(
                "Pull Merino", null, "top", "FR",
                null, null,
                List.of(new MaterialRequest("wool", 100, "France")), List.of(), null,
                "2026-01-01", null, null, null, false,
                null, null, null, false, null, 1
        );
    }

    @Test
    void update_shouldGeocodeMaterialsToo() {
        UUID id = UUID.randomUUID();
        DppForm form = new DppForm();
        form.setId(id);
        form.setUser(user);
        form.setStatus(DppStatus.DRAFT);
        when(dppFormRepository.findById(id)).thenReturn(Optional.of(form));
        when(geocodingService.geocode("France"))
                .thenReturn(Optional.of(new GeocodingService.Coordinates(46.6, 1.88)));

        service.update(id, requestWithMaterial(), Collections.emptyMap(), USER_EMAIL);

        ArgumentCaptor<DppMaterial> saved = ArgumentCaptor.forClass(DppMaterial.class);
        verify(dppMaterialRepository).save(saved.capture());
        assertThat(saved.getValue().getLatitude()).isEqualTo(46.6);
        assertThat(saved.getValue().getLongitude()).isEqualTo(1.88);
    }

    @Test
    void createDraft_shouldGeocodeMaterials() {
        when(geocodingService.geocode("France"))
                .thenReturn(Optional.of(new GeocodingService.Coordinates(46.6, 1.88)));

        service.create(requestWithMaterial(), Collections.emptyMap(), USER_EMAIL, true);

        ArgumentCaptor<DppForm> saved = ArgumentCaptor.forClass(DppForm.class);
        verify(dppFormRepository).save(saved.capture());
        assertThat(saved.getValue().getMaterials())
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.getLatitude()).isEqualTo(46.6);
                    assertThat(m.getLongitude()).isEqualTo(1.88);
                });
    }

    // ── cloisonnement des documents ───────────────────────────────────────────

    @Test
    void findByPublicCode_shouldOnlyExposePublicDocuments() {
        DppForm form = formWithOneDocumentPerVisibility("SEED0001");
        when(dppFormRepository.findByPublicCode("SEED0001")).thenReturn(Optional.of(form));
        when(artisanProfileRepository.findByUser(user)).thenReturn(Optional.empty());
        when(irisScoreRepository.findByDppFormId(form.getId())).thenReturn(Optional.empty());
        when(accessTokenService.resolve("SEED0001", null)).thenReturn(DppAccessLevel.PUBLIC);

        DppFormPublicResponse response = service.findByPublicCode("SEED0001", null);

        assertThat(response.dpp().documents())
                .singleElement()
                .satisfies(d -> assertThat(d.visibility()).isEqualTo("PUBLIC_USERS"));
        assertThat(response.accessLevel()).isEqualTo(DppAccessLevel.PUBLIC);
    }

    @Test
    void findByPublicCode_shouldWidenToCircularDocuments_whenTheTokenResolves() {
        DppForm form = formWithOneDocumentPerVisibility("SEED0001");
        when(dppFormRepository.findByPublicCode("SEED0001")).thenReturn(Optional.of(form));
        when(artisanProfileRepository.findByUser(user)).thenReturn(Optional.empty());
        when(irisScoreRepository.findByDppFormId(form.getId())).thenReturn(Optional.empty());
        when(accessTokenService.resolve("SEED0001", "tok")).thenReturn(DppAccessLevel.CIRCULAR_OPERATORS);

        DppFormPublicResponse response = service.findByPublicCode("SEED0001", "tok");

        // Cumulatif jusqu'au niveau accordé, et pas un cran de plus.
        assertThat(response.dpp().documents())
                .extracting(DppFormDocumentResponse::visibility)
                .containsExactlyInAnyOrder("PUBLIC_USERS", "CIRCULAR_OPERATORS");
        verify(storageService, never()).getPresignedUrl(documentFileId(form, DppDocumentVisibility.AUTHORITIES));
    }

    /**
     * Une URL présignée émise est un accès accordé : la signer pour un document hors périmètre
     * suffit à le divulguer, que le front l'affiche ou non.
     */
    @Test
    void findByPublicCode_shouldNotSignUrlsForRestrictedDocuments() {
        DppForm form = formWithOneDocumentPerVisibility("SEED0001");
        when(dppFormRepository.findByPublicCode("SEED0001")).thenReturn(Optional.of(form));
        when(artisanProfileRepository.findByUser(user)).thenReturn(Optional.empty());
        when(irisScoreRepository.findByDppFormId(form.getId())).thenReturn(Optional.empty());

        when(accessTokenService.resolve("SEED0001", null)).thenReturn(DppAccessLevel.PUBLIC);

        service.findByPublicCode("SEED0001", null);

        UUID publicFileId = documentFileId(form, DppDocumentVisibility.PUBLIC_USERS);
        verify(storageService).getPresignedUrl(publicFileId);
        verify(storageService, never()).getPresignedUrl(documentFileId(form, DppDocumentVisibility.CIRCULAR_OPERATORS));
        verify(storageService, never()).getPresignedUrl(documentFileId(form, DppDocumentVisibility.AUTHORITIES));
    }

    @Test
    void findById_shouldExposeEveryDocumentToTheOwner() {
        DppForm form = formWithOneDocumentPerVisibility("SEED0001");
        when(dppFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(artisanProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThat(service.findById(form.getId(), USER_EMAIL).documents()).hasSize(3);
    }

    private DppForm formWithOneDocumentPerVisibility(String publicCode) {
        DppForm form = new DppForm();
        form.setId(UUID.randomUUID());
        form.setUser(user);
        form.setPublicCode(publicCode);
        form.getDocuments().add(document(form, DocumentType.CARE_GUIDE, DppDocumentVisibility.PUBLIC_USERS));
        form.getDocuments().add(document(form, DocumentType.REPAIR_MANUAL, DppDocumentVisibility.CIRCULAR_OPERATORS));
        form.getDocuments().add(document(form, DocumentType.SALE_INVOICE, DppDocumentVisibility.AUTHORITIES));
        return form;
    }

    private static DppFormDocument document(DppForm form, DocumentType type, DppDocumentVisibility visibility) {
        StoredFile file = new StoredFile();
        file.setId(UUID.randomUUID());
        file.setOriginalFilename(type.partName + ".pdf");

        DppFormDocument doc = new DppFormDocument();
        doc.setDppForm(form);
        doc.setFile(file);
        doc.setDocumentType(type);
        doc.setVisibility(visibility);
        return doc;
    }

    private static UUID documentFileId(DppForm form, DppDocumentVisibility visibility) {
        return form.getDocuments().stream()
                .filter(d -> d.getVisibility() == visibility)
                .findFirst()
                .orElseThrow()
                .getFile()
                .getId();
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
