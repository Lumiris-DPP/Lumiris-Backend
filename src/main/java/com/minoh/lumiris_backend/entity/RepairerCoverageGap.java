package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// Une recherche géolocalisée de retoucheur restée sans résultat — un point de demande non
// satisfaite. Anonyme : pas d'utilisateur, juste le lieu et l'heure.
@Entity
@Table(name = "repairer_coverage_gap")
public class RepairerCoverageGap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    private String specialty;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public RepairerCoverageGap() {}

    public RepairerCoverageGap(double lat, double lng, String specialty) {
        this.lat = lat;
        this.lng = lng;
        this.specialty = specialty;
    }
}
