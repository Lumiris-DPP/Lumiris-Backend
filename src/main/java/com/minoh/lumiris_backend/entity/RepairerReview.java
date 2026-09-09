package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repairer_reviews")
@Getter
@Setter
public class RepairerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repairer_profile_id", nullable = false)
    private RepairerProfile repairerProfile;

    @Column(nullable = false)
    private int rating;

    private String comment;

    @Column(name = "reviewer_name")
    private String reviewerName;

    // Rattachement à l'intervention terminée qui donne droit à l'avis. Null pour les avis
    // historiques (seed / imports). Un avis « vérifié » = repairRequestId non null.
    @Column(name = "repair_request_id")
    private UUID repairRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
