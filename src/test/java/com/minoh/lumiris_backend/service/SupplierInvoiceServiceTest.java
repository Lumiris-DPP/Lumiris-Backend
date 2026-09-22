package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.dto.out.SupplierInvoiceResponse;
import com.minoh.lumiris_backend.entity.StoredFile;
import com.minoh.lumiris_backend.entity.SupplierInvoice;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.SupplierInvoiceRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierInvoiceServiceTest {

    @Mock private SupplierInvoiceRepository supplierInvoiceRepository;
    @Mock private UserRepository userRepository;
    @Mock private StoredFileRepository storedFileRepository;
    @Mock private StorageService storageService;
    @Mock private OcrService ocrService;
    @Mock private SupplierInvoiceOcrParser ocrParser;

    @InjectMocks
    private SupplierInvoiceService service;

    private static final String USER_EMAIL = "artisan@test.com";
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(USER_EMAIL);

        lenient().when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
        lenient().when(supplierInvoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void upload_persistsTheParsedExtraction_whenOcrProducesText() {
        MockMultipartFile file = new MockMultipartFile("file", "facture.jpg", "image/jpeg", new byte[] { 1 });
        UUID fileId = UUID.randomUUID();
        when(storageService.upload(file, USER_EMAIL)).thenReturn(
                new FileUploadResponse(fileId, "facture.jpg", "image/jpeg", 1L, Instant.now()));
        StoredFile storedFile = new StoredFile();
        storedFile.setId(fileId);
        when(storedFileRepository.getReferenceById(fileId)).thenReturn(storedFile);
        when(ocrService.extractText(file)).thenReturn(Optional.of("Atelier Test\nTotal HT : 100,00 €"));
        when(ocrParser.parse("Atelier Test\nTotal HT : 100,00 €")).thenReturn(new SupplierInvoiceOcrParser.ParsedInvoice(
                "Atelier Test", null, new java.math.BigDecimal("100.00"), "EUR", java.util.List.of()));
        when(storageService.getPresignedUrl(fileId)).thenReturn("https://files.test/facture.jpg");

        SupplierInvoiceResponse response = service.upload(USER_EMAIL, file);

        assertThat(response.fileUrl()).isEqualTo("https://files.test/facture.jpg");
        assertThat(response.ocrExtracted()).isNotNull();
        assertThat(response.ocrExtracted().supplierName()).isEqualTo("Atelier Test");
    }

    @Test
    void upload_leavesExtractionNull_whenOcrFindsNoText() {
        MockMultipartFile file = new MockMultipartFile("file", "facture.pdf", "application/pdf", new byte[] { 1 });
        UUID fileId = UUID.randomUUID();
        when(storageService.upload(file, USER_EMAIL)).thenReturn(
                new FileUploadResponse(fileId, "facture.pdf", "application/pdf", 1L, Instant.now()));
        StoredFile storedFile = new StoredFile();
        storedFile.setId(fileId);
        when(storedFileRepository.getReferenceById(fileId)).thenReturn(storedFile);
        when(ocrService.extractText(file)).thenReturn(Optional.empty());
        when(storageService.getPresignedUrl(fileId)).thenReturn("https://files.test/facture.pdf");

        SupplierInvoiceResponse response = service.upload(USER_EMAIL, file);

        assertThat(response.ocrExtracted()).isNull();
    }

    @Test
    void findOne_throwsNotFound_whenInvoiceBelongsToSomeoneElse() {
        UUID id = UUID.randomUUID();
        when(supplierInvoiceRepository.findByIdAndUploadedBy(id, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOne(USER_EMAIL, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findOne_returnsTheInvoice_whenOwnedByTheCaller() {
        UUID id = UUID.randomUUID();
        SupplierInvoice invoice = new SupplierInvoice();
        invoice.setId(id);
        invoice.setUploadedBy(user);
        StoredFile storedFile = new StoredFile();
        storedFile.setId(UUID.randomUUID());
        invoice.setFile(storedFile);
        when(supplierInvoiceRepository.findByIdAndUploadedBy(id, user)).thenReturn(Optional.of(invoice));
        when(storageService.getPresignedUrl(storedFile.getId())).thenReturn("https://files.test/facture.jpg");

        SupplierInvoiceResponse response = service.findOne(USER_EMAIL, id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.ocrExtracted()).isNull();
    }
}
