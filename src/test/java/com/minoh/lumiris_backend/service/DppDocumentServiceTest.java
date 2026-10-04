package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.DppFormRequest;
import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.entity.CertificateType;
import com.minoh.lumiris_backend.entity.DocumentType;
import com.minoh.lumiris_backend.entity.DppDocumentVisibility;
import com.minoh.lumiris_backend.entity.DppForm;
import com.minoh.lumiris_backend.entity.DppFormDocument;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.DppFormDocumentRepository;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DppDocumentServiceTest {

    private static final String USER_EMAIL = "artisan@test.com";

    @Mock
    private StorageService storageService;

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private DppFormDocumentRepository dppFormDocumentRepository;

    @Mock
    private CertificateLibraryService certificateLibraryService;

    @InjectMocks
    private DppDocumentService service;

    @Test
    void upload_shouldSkipEmptyParts() {
        UUID storedId = UUID.randomUUID();
        MultipartFile careGuide = new MockMultipartFile("careGuide", "care.pdf", "application/pdf", "%PDF".getBytes());
        MultipartFile emptyManual = new MockMultipartFile("repairManual", "manual.pdf", "application/pdf", new byte[0]);
        when(storageService.upload(careGuide, USER_EMAIL))
                .thenReturn(new FileUploadResponse(storedId, "care.pdf", "application/pdf", 4, null));

        Map<String, UUID> uploaded = service.upload(
                Map.of("careGuide", careGuide, "repairManual", emptyManual), USER_EMAIL);

        assertThat(uploaded).containsExactly(Map.entry("careGuide", storedId));
        verify(storageService, never()).upload(emptyManual, USER_EMAIL);
    }

    @Test
    void saveDocuments_shouldApplyTheDefaultVisibility_andLeaveTheMainPhotoToTheForm() {
        DppForm form = new DppForm();
        UUID photoId = UUID.randomUUID();
        UUID manualId = UUID.randomUUID();
        Map<String, UUID> uploadedIds = new LinkedHashMap<>();
        uploadedIds.put("productPhoto", photoId);
        uploadedIds.put("repairManual", manualId);

        service.saveDocuments(form, uploadedIds, true);

        ArgumentCaptor<DppFormDocument> saved = ArgumentCaptor.forClass(DppFormDocument.class);
        verify(dppFormDocumentRepository).save(saved.capture());
        assertThat(saved.getValue().getDocumentType()).isEqualTo(DocumentType.REPAIR_MANUAL);
        assertThat(saved.getValue().getVisibility()).isEqualTo(DppDocumentVisibility.CIRCULAR_OPERATORS);
        // replaceExisting : le document du même type est remplacé, pas empilé.
        verify(dppFormDocumentRepository).deleteByDppFormAndDocumentType(form, DocumentType.REPAIR_MANUAL);
        verify(storedFileRepository, never()).getReferenceById(photoId);
    }

    @Test
    void resolveCertificateLibraryRefs_shouldRefuseAFileAndALibraryCertificateForTheSameSlot() {
        UUID libraryId = UUID.randomUUID();
        MultipartFile upload = new MockMultipartFile(
                "originCerts", "origin.pdf", "application/pdf", "%PDF".getBytes());

        assertThatThrownBy(() -> service.resolveCertificateLibraryRefs(
                requestWithOriginCertificate(libraryId), Map.of("originCerts", upload),
                new LinkedHashMap<>(), new User()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(certificateLibraryService, never()).resolveForAttach(any(), any(), any());
    }

    @Test
    void resolveCertificateLibraryRefs_shouldAttachTheLibraryFileUnderItsPartName() {
        UUID libraryId = UUID.randomUUID();
        User user = new User();
        StoredFile file = new StoredFile();
        file.setId(UUID.randomUUID());
        when(certificateLibraryService.resolveForAttach(libraryId, CertificateType.ORIGIN, user)).thenReturn(file);
        Map<String, UUID> uploadedIds = new LinkedHashMap<>();

        service.resolveCertificateLibraryRefs(requestWithOriginCertificate(libraryId), Map.of(), uploadedIds, user);

        assertThat(uploadedIds).containsExactly(Map.entry("originCerts", file.getId()));
    }

    private static DppFormRequest requestWithOriginCertificate(UUID libraryId) {
        return new DppFormRequest(
                "Pull Merino", null, "top", "FR",
                null, null,
                null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, 1,
                null, libraryId
        );
    }
}
