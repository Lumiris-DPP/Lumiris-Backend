package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dpp_iris_scores")
@Getter
public class IrisScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dpp_form_id", nullable = false)
    private DppForm dppForm;

    @Column(nullable = false)
    private double transparency;

    @Column(nullable = false)
    private double craftsmanship;

    @Column(nullable = false)
    private double repairability;

    @Column(nullable = false)
    private double impact;

    @Column(nullable = false)
    private double total;

    @Column(nullable = false)
    private String grade;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    protected IrisScore() {}

    public IrisScore(DppForm dppForm, double transparency, double craftsmanship,
                     double repairability, double impact, double total, String grade) {
        this.dppForm = dppForm;
        this.transparency = transparency;
        this.craftsmanship = craftsmanship;
        this.repairability = repairability;
        this.impact = impact;
        this.total = total;
        this.grade = grade;
        this.computedAt = Instant.now();
    }
}
