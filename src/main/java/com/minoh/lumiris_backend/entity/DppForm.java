package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Column(name = "public_code", unique = true, nullable = false, length = 8)
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

    @Column(name = "data_hash", length = 64, nullable = false)
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
}
