package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
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
}
