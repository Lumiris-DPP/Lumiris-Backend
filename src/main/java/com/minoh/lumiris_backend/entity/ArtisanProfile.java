package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "artisan_profiles")
@Getter
@Setter
public class ArtisanProfile extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "atelier_name")
    private String atelierName;

    @Column(name = "display_name")
    private String displayName;

    @Column(unique = true)
    private String slug;

    private String city;

    private String region;

    @Column(nullable = false)
    private String tier = "Solo";

    @Column(nullable = false)
    private boolean plus = false;

    @Column(name = "passport_limit", nullable = false)
    private int passportLimit = 50;

    private String story;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "epv_labeled", nullable = false)
    private boolean epvLabeled = false;

    @Column(name = "ofg_labeled", nullable = false)
    private boolean ofgLabeled = false;

    @Column(name = "gots_labeled", nullable = false)
    private boolean gotsLabeled = false;

    @Column(name = "oeko_tex_labeled", nullable = false)
    private boolean oekoTexLabeled = false;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    // Onboarding fields
    @Column(length = 14)
    private String siret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtisanStatus status = ArtisanStatus.PENDING;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "naf_code", length = 10)
    private String nafCode;

    @Column(name = "declaration_signed", nullable = false)
    private boolean declarationSigned = false;

    @Column(name = "signature_timestamp")
    private Instant signatureTimestamp;

    @Column(name = "signature_ip", length = 45)
    private String signatureIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sirene_raw_data", columnDefinition = "jsonb")
    private String sireneRawData;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // Vitrine publique
    private String method;

    private String journey;

    @Column(columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> specialties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> links;

    @Column(nullable = false)
    private boolean published = false;

    @Embedded
    private KybDetails kyb = new KybDetails();

    // Hibernate leaves `kyb` null after loading a row where every kyb_* column is still NULL
    // (no KYB dossier submitted yet) instead of using the field initializer above — guarantee
    // callers never see a null embeddable regardless of load path.
    @PostLoad
    private void initKyb() {
        if (kyb == null) kyb = new KybDetails();
    }

    // Congés : les pièces restent achetables, le délai d'expédition annoncé est allongé jusqu'à
    // cette date. La pause se termine d'elle-même quand la date passe — aucun job de reprise.
    @Column(name = "paused_until")
    private Instant pausedUntil;
}
