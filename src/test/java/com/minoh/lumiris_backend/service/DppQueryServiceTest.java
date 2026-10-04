package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.DppFormDocumentResponse;
import com.minoh.lumiris_backend.dto.out.DppFormPublicResponse;
import com.minoh.lumiris_backend.dto.out.DppPublicJsonLdResponse;
import com.minoh.lumiris_backend.dto.out.DppVerificationResponse;
import com.minoh.lumiris_backend.entity.BlockchainAnchorStatus;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.DppAccessLevel;
import com.minoh.lumiris_backend.entity.DppDocumentVisibility;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppFormDocument;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.mapper.DppFormMapper;
import com.minoh.lumiris_backend.repository.ArtisanProfileRepository;
import com.minoh.lumiris_backend.repository.DppFormDocumentRepository;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.IrisScoreRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import com.minoh.lumiris_backend.service.scoring.IrisScoreCalculator;
import com.minoh.lumiris_backend.util.DppHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DppQueryServiceTest {

    @Mock
    private DppFormRepository dppFormRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ArtisanProfileRepository artisanProfileRepository;

    @Mock
    private IrisScoreRepository irisScoreRepository;

    @Mock
    private RepairRequestRepository repairRequestRepository;

    @Mock
    private AtelierStatsService atelierStatsService;

    @Mock
    private DppAccessTokenService accessTokenService;

    @Mock
    private IrisScoreCalculator irisScoreCalculator;

    @Mock
    private DppHashUtil dppHashUtil;

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private StorageService storageService;

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private DppFormDocumentRepository dppFormDocumentRepository;

    @Mock
    private CertificateLibraryService certificateLibraryService;

    private DppQueryService service;

    private static final String USER_EMAIL = "artisan@test.com";
    private User user;

    @BeforeEach
    void setUp() {
        // DppDocumentService réel : c'est lui qui filtre les documents et signe les URL, et ce
        // filtrage est précisément ce que les tests de cloisonnement vérifient.
        DppDocumentService documentService = new DppDocumentService(
                storageService, storedFileRepository, dppFormDocumentRepository, certificateLibraryService);
        DppOwnershipGuard ownershipGuard = new DppOwnershipGuard(userRepository, dppFormRepository);
        service = new DppQueryService(
                dppFormRepository, userRepository, artisanProfileRepository, irisScoreRepository,
                repairRequestRepository, atelierStatsService, accessTokenService,
                new DppFormMapper(mock(GeocodingService.class)), irisScoreCalculator, dppHashUtil,
                blockchainService, documentService, ownershipGuard);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(USER_EMAIL);

        lenient().when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
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
        assertThat(signedFileIds())
                .doesNotContain(documentFileId(form, DppDocumentVisibility.AUTHORITIES));
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
        assertThat(signedFileIds()).containsExactly(publicFileId);
    }

    @Test
    void findPublicJsonLd_shouldExposeOnlyThePublicScope() {
        DppForm form = formWithOneDocumentPerVisibility("SEED0001");
        UUID publicFileId = documentFileId(form, DppDocumentVisibility.PUBLIC_USERS);

        when(dppFormRepository.findByPublicCode("SEED0001")).thenReturn(Optional.of(form));
        when(storageService.getPresignedUrls(any()))
                .thenReturn(Map.of(publicFileId, "https://files.test/care-guide.pdf"));

        DppPublicJsonLdResponse response = service.findPublicJsonLd(
                "SEED0001",
                "https://api.lumiris.eu/public/dpp_forms/SEED0001/jsonld"
        );

        assertThat(response.id()).isEqualTo("https://api.lumiris.eu/public/dpp_forms/SEED0001/jsonld");
        assertThat(response.context()).containsEntry("@vocab", "https://schema.org/");
        assertThat(response.context()).containsEntry("materials", "lumiris:composition");
        assertThat(response.context().get("image")).isEqualTo(Map.of(
                "@id", "https://schema.org/image",
                "@type", "@id"
        ));
        assertThat(response.documents())
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.documentType()).isEqualTo("CARE_GUIDE");
                    assertThat(document.url()).isEqualTo("https://files.test/care-guide.pdf");
                });

        verify(accessTokenService, never()).resolve(any(), any());
        verify(atelierStatsService, never()).trackScan(any());
        assertThat(signedFileIds()).containsExactly(publicFileId);
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

    // Les identifiants réellement soumis à la signature MinIO : c'est eux, et non le DTO rendu,
    // qui disent quels documents ont été divulgués.
    @SuppressWarnings("unchecked")
    private List<UUID> signedFileIds() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(storageService).getPresignedUrls(captor.capture());
        return List.copyOf(captor.getValue());
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
