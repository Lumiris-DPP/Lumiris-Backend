package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dpp_materials")
@Getter
@Setter
@NoArgsConstructor
public class DppMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id", nullable = false)
    private DppForm dppForm;

    @Column(nullable = false)
    private String fiber;

    @Column(nullable = false)
    private Integer percentage;

    @Column(name = "origin_country")
    private String originCountry;
}
