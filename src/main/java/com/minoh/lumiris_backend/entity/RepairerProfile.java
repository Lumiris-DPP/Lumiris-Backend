package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repairer_profiles")
@Getter
@Setter
public class RepairerProfile extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Nullable: CMA-imported directory listings have no Lumiris account until claimed/signed up.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "display_name")
    private String displayName;

    @Column(length = 14)
    private String siret;

    @Column(name = "company_name")
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairerStatus status = RepairerStatus.PENDING;

    @Column(columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> specialties;

    @Column(columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> zones;

    private String schedule;

    private String address;

    private String city;

    private String region;

    private Point location;

    @Embedded
    private KybDetails kyb = new KybDetails();

    // Hibernate leaves `kyb` null after loading a row where every kyb_* column is still NULL
    // (no KYB dossier submitted yet) instead of using the field initializer above — guarantee
    // callers never see a null embeddable regardless of load path.
    @PostLoad
    private void initKyb() {
        if (kyb == null) kyb = new KybDetails();
    }
}
