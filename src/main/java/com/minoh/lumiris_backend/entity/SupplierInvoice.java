package com.minoh.lumiris_backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "supplier_invoices")
@Getter
@Setter
public class SupplierInvoice extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;

    // Texte brut renvoyé par OcrService, conservé pour permettre un re-parsing (heuristique
    // améliorée plus tard) sans redemander le fichier ni relancer Tesseract.
    @Column(name = "ocr_raw_text", columnDefinition = "TEXT")
    private String ocrRawText;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "total_ht", precision = 12, scale = 2)
    private BigDecimal totalHt;

    @Column(name = "currency")
    private String currency;

    @OneToMany(mappedBy = "supplierInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "line_order")
    private List<SupplierInvoiceLineItem> lineItems = new ArrayList<>();

    // null tant que l'extraction n'a rien trouvé d'exploitable (échec OCR, ou texte reconnu mais
    // aucun champ détecté) — distinct d'une extraction dont les champs sont simplement vides.
    public boolean hasOcrExtraction() {
        return supplierName != null || invoiceDate != null || totalHt != null || !lineItems.isEmpty();
    }
}
