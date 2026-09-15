package com.minoh.lumiris_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    // RGPD — suppression douce : posé au DELETE /me, déclenche la révocation des sessions et
    // bloque la connexion. anonymizedAt est posé plus tard par AccountPurgeScheduler.
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private ArtisanProfile artisanProfile;
}
