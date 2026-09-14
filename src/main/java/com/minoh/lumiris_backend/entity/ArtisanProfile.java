package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

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

    // Null tant que la fiche est un import annuaire non réclamé (voir ArtisanStatus.UNCLAIMED).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "atelier_name")
    private String atelierName;

    @Column(name = "display_name")
    private String displayName;

    @Column(unique = true)
    private String slug;

    private String city;

    private String region;

    // Fiches SIRENE géocodées uniquement — pas de saisie manuelle côté onboarding SELF.
    private Point location;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtisanSource source = ArtisanSource.SELF;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "imported_at")
    private Instant importedAt;

    @Column(name = "interest_count", nullable = false)
    private int interestCount = 0;

    // Congés : les pièces restent achetables, le délai d'expédition annoncé est allongé jusqu'à
    // cette date. La pause se termine d'elle-même quand la date passe — aucun job de reprise.
    @Column(name = "paused_until")
    private Instant pausedUntil;

    @Embedded
    private KybDetails kyb = new KybDetails();

    // Hibernate leaves `kyb` null after loading a row where every kyb_* column is still NULL
    // (no KYB dossier submitted yet) instead of using the field initializer above — guarantee
    // callers never see a null embeddable regardless of load path.
    @PostLoad
    private void initKyb() {
        if (kyb == null) kyb = new KybDetails();
    }

    // ── Adresse d'enlèvement (expéditeur du bordereau) ──────────────────────
    // Distincte de `city`, qui est une donnée de vitrine : un atelier expose sa ville sans publier
    // sa rue. Sans elle, aucune étiquette n'est fabricable.
    @Column(name = "ship_from_line1", length = 300)
    private String shipFromLine1;

    @Column(name = "ship_from_line2", length = 300)
    private String shipFromLine2;

    @Column(name = "ship_from_postal_code", length = 20)
    private String shipFromPostalCode;

    @Column(name = "ship_from_city", length = 120)
    private String shipFromCity;

    @Column(name = "ship_from_country", length = 2)
    private String shipFromCountry = "FR";

    @Column(name = "ship_from_phone", length = 40)
    private String shipFromPhone;

    public boolean hasShipFromAddress() {
        return isFilled(shipFromLine1) && isFilled(shipFromPostalCode) && isFilled(shipFromCity);
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
