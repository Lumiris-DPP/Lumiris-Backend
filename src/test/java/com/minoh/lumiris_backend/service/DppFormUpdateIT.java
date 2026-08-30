package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.DppFormCreatedResponse;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Édition d'un brouillon avec pièces jointes, sur un vrai PostgreSQL (contraintes + triggers réels).
 * Reproduit le 500 « null value in column dpp_form_id » remonté à l'ajout d'un document sur un
 * brouillon existant : le chemin PUT est le seul à passer par une collection déjà chargée.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "stripe.secret-key=sk_test_dummy",
        "stripe.publishable-key=pk_test_dummy",
        "stripe.webhook-secret=whsec_dummy",
        "stripe.bootstrap-catalog=false",
        "stripe.products.solo=prod_dummy",
        "stripe.products.studio=prod_dummy",
        "stripe.products.maison=prod_dummy",
        "stripe.products.atelier-plus=prod_dummy",
        "stripe.products.local=prod_dummy",
        "blockchain.wallet.private-key=0x0000000000000000000000000000000000000000000000000000000000000001",
})
class DppFormUpdateIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DppFormService dppFormService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private BlockchainService blockchainService;

    private static final String EMAIL = "artisan-it@lumiris.test";
    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.findByEmail(EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(EMAIL);
            u.setPasswordHash("{noop}irrelevant");
            u.setRole(UserRole.ARTISAN);
            return userRepository.save(u);
        });

        // Chaque upload persiste un vrai fichier : le service n'attache qu'un id.
        Mockito.when(storageService.upload(Mockito.any(), Mockito.eq(EMAIL))).thenAnswer(inv -> {
            MultipartFile file = inv.getArgument(0);
            StoredFile stored = new StoredFile();
            stored.setBucketName("test");
            stored.setObjectKey(UUID.randomUUID().toString());
            stored.setOriginalFilename(file.getOriginalFilename());
            stored.setContentType(file.getContentType());
            stored.setSizeBytes(file.getSize());
            stored.setUploadedBy(user);
            StoredFile saved = storedFileRepository.save(stored);
            return new FileUploadResponse(saved.getId(), saved.getOriginalFilename(),
                    saved.getContentType(), saved.getSizeBytes(), null);
        });
    }

    private static MultipartFile pdf(String name) {
        return new MockMultipartFile(name, name + ".pdf", "application/pdf", "%PDF-1.4".getBytes());
    }

    private static DppFormRequest request() {
        return new DppFormRequest(
                "Pull Merino", "Un pull doux", "top", "FR",
                List.of("M"), List.of("Écru"),
                List.of(), List.of(), null,
                "2026-01-01", "LOT-001", null, "SKU-001", false,
                null, null, null, null, false, null, 1,
                null, null
        );
    }

    @Test
    void update_shouldAttachANewDocumentToAnExistingDraft() {
        DppFormCreatedResponse created = dppFormService.create(
                request(), Map.of("careGuide", pdf("careGuide")), EMAIL, true);

        assertThatCode(() -> dppFormService.update(
                created.id(), request(), Map.of("repairManual", pdf("repairManual")), EMAIL))
                .doesNotThrowAnyException();

        assertThat(dppFormService.findById(created.id(), EMAIL).documents())
                .extracting(d -> d.documentType())
                .containsExactlyInAnyOrder("CARE_GUIDE", "REPAIR_MANUAL");
    }

    @Test
    void update_shouldReplaceADocumentOfTheSameType() {
        DppFormCreatedResponse created = dppFormService.create(
                request(), Map.of("careGuide", pdf("careGuide")), EMAIL, true);

        dppFormService.update(created.id(), request(), Map.of("careGuide", pdf("careGuide-v2")), EMAIL);

        assertThat(dppFormService.findById(created.id(), EMAIL).documents())
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.documentType()).isEqualTo("CARE_GUIDE");
                    assertThat(d.filename()).isEqualTo("careGuide-v2.pdf");
                });
    }
}
