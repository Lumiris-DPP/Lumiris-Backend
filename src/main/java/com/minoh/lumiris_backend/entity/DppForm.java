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

    @Column(name = "main_photo_url")
    private String mainPhotoUrl;

    @Column(name = "manufactured_at")
    private String manufacturedAt;

    @Column(name = "batch_number")
    private String batchNumber;

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

    @OneToMany(mappedBy = "dppForm", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<DppMaterial> materials = new ArrayList<>();

    @OneToMany(mappedBy = "dppForm", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<DppCareInstruction> careInstructions = new ArrayList<>();

    @OneToMany(mappedBy = "dppForm", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<DppCertification> certifications = new ArrayList<>();
}
