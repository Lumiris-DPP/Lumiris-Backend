package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dpp_certifications")
@Getter
@Setter
@NoArgsConstructor
public class DppCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id", nullable = false)
    private DppForm dppForm;

    @Column(nullable = false)
    private String name;

    @Column(name = "custom_name")
    private String customName;

    @Column(name = "license_number")
    private String licenseNumber;
}
