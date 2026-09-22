package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.out.FileUploadResponse;
import com.minoh.lumiris_backend.dto.out.OcrExtractionResponse;
import com.minoh.lumiris_backend.dto.out.OcrLineItemResponse;
import com.minoh.lumiris_backend.dto.out.SupplierInvoiceResponse;
import com.minoh.lumiris_backend.entity.SupplierInvoice;
import com.minoh.lumiris_backend.entity.SupplierInvoiceLineItem;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.StoredFileRepository;
import com.minoh.lumiris_backend.repository.SupplierInvoiceRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// Devis/factures fournisseur d'un artisan : upload -> OCR best-effort (Tesseract, cf. OcrService)
// -> extraction heuristique (SupplierInvoiceOcrParser) -> stocké, consultable par l'artisan.
// Aucun lien avec un DppForm/Material pour l'instant — la fiche passeport ne référence pas encore
// de facture ; ce sera un chantier séparé une fois ce backend en place.
@Service
@RequiredArgsConstructor
public class SupplierInvoiceService {

    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final StorageService storageService;
    private final OcrService ocrService;
    private final SupplierInvoiceOcrParser ocrParser;

    @Transactional
    public SupplierInvoiceResponse upload(String userEmail, MultipartFile file) {
        User user = findUser(userEmail);
        FileUploadResponse uploaded = storageService.upload(file, userEmail);

        SupplierInvoice invoice = new SupplierInvoice();
        invoice.setUploadedBy(user);
        invoice.setFile(storedFileRepository.getReferenceById(uploaded.id()));

        ocrService.extractText(file).ifPresent(rawText -> applyExtraction(invoice, rawText));

        return toResponse(supplierInvoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public List<SupplierInvoiceResponse> findMine(String userEmail) {
        User user = findUser(userEmail);
        return supplierInvoiceRepository.findByUploadedByOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceResponse findOne(String userEmail, UUID id) {
        User user = findUser(userEmail);
        SupplierInvoice invoice = supplierInvoiceRepository.findByIdAndUploadedBy(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Facture introuvable"));
        return toResponse(invoice);
    }

    private void applyExtraction(SupplierInvoice invoice, String rawText) {
        invoice.setOcrRawText(rawText);
        SupplierInvoiceOcrParser.ParsedInvoice parsed = ocrParser.parse(rawText);

        invoice.setSupplierName(parsed.supplierName());
        invoice.setInvoiceDate(parsed.invoiceDate());
        invoice.setTotalHt(parsed.totalHt());
        invoice.setCurrency(parsed.currency());

        for (SupplierInvoiceOcrParser.ParsedLineItem line : parsed.lineItems()) {
            SupplierInvoiceLineItem entity = new SupplierInvoiceLineItem();
            entity.setSupplierInvoice(invoice);
            entity.setFiber(line.fiber());
            entity.setLabel(line.label());
            entity.setQty(line.qty());
            entity.setUnit(line.unit());
            invoice.getLineItems().add(entity);
        }
    }

    private SupplierInvoiceResponse toResponse(SupplierInvoice invoice) {
        OcrExtractionResponse extraction = invoice.hasOcrExtraction()
                ? new OcrExtractionResponse(
                        invoice.getSupplierName(),
                        invoice.getInvoiceDate(),
                        invoice.getTotalHt(),
                        invoice.getCurrency(),
                        invoice.getLineItems().stream()
                                .map(l -> new OcrLineItemResponse(l.getFiber(), l.getLabel(), l.getQty(), l.getUnit()))
                                .toList())
                : null;

        return new SupplierInvoiceResponse(
                invoice.getId(),
                storageService.getPresignedUrl(invoice.getFile().getId()),
                extraction,
                invoice.getCreatedAt());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
