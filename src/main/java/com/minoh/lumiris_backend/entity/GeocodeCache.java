package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "geocode_cache")
@Getter
public class GeocodeCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_normalized", nullable = false, unique = true)
    private String queryNormalized;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(nullable = false)
    private String provider;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    protected GeocodeCache() {}

    public GeocodeCache(String queryNormalized, Double latitude, Double longitude, String provider) {
        this.queryNormalized = queryNormalized;
        this.latitude = latitude;
        this.longitude = longitude;
        this.provider = provider;
        this.resolvedAt = Instant.now();
    }
}