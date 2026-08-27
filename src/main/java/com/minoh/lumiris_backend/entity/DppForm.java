package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.minoh.lumiris_backend.entity.DppStatus.VALID;

@Entity
@Table(name = "dpp_forms")
@Getter
@Setter
public class DppForm extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "dpp_status")
    private DppStatus status = VALID;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_description")
    private String productDescription;

    @Column(name = "product_category")
    private String productCategory;

    @Column(name = "origin_country")
    private String originCountry;

    @Column(name = "manufactured_at")
    private String manufacturedAt;

    @Column(name = "batch_number")
    private String batchNumber;
    @Column(nullable = false)
    private int quantity = 1;

    // Null while the form is a DRAFT — the QR identity only exists after publication.
    @Column(name = "public_code", unique = true, length = 8)
    private String publicCode;

    @Column(unique = true)
    private String gtin;

    @Column
    private String sku;

    @Column(name = "reach_compliant")
    private Boolean reachCompliant;

    @Column(name = "recycled_pct")
    private Integer recycledPct;

    @Column(name = "warranty_description")
    private String warrantyDescription;

    // Durée chiffrée de la garantie. warrantyDescription est une phrase libre : on ne déduit pas
    // une échéance d'alerte de la prose. Null = aucune durée déclarée, donc aucune alerte.
    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "is_repairable")
    private Boolean isRepairable;

    @Column(name = "end_of_life_instructions")
    private String endOfLifeInstructions;

    @Column(name = "available_sizes", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> availableSizes;

    @Column(name = "colors", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> colors;

    // Null while the form is a DRAFT — the hash is frozen at publication.
    @Column(name = "data_hash", length = 64)
    private String dataHash;

    @Column(name = "blockchain_tx_hash", length = 66)
    private String blockchainTxHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "blockchain_anchor_status", nullable = false, length = 20)
    private BlockchainAnchorStatus blockchainAnchorStatus = BlockchainAnchorStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_photo_file_id")
    private StoredFile mainPhotoFile;

    @OneToMany(mappedBy = "dppForm", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<DppMaterial> materials = new ArrayList<>();

    @Column(name = "care_notes")
    private String careNotes;

    @OneToMany(mappedBy = "dppForm", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<DppCareInstruction> careInstructions = new ArrayList<>();

    @OneToMany(mappedBy = "dppForm", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<DppFormDocument> documents = new ArrayList<>();

    public Set<DocumentType> attachedDocumentTypes() {
        Set<DocumentType> types = documents.stream()
                .map(DppFormDocument::getDocumentType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentType.class)));
        if (mainPhotoFile != null) {
            types.add(DocumentType.PRODUCT_PHOTO);
        }
        return types;
    }
}
