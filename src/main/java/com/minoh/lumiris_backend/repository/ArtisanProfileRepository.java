package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtisanProfileRepository extends JpaRepository<ArtisanProfile, UUID> {
    Optional<ArtisanProfile> findByUser(User user);
    List<ArtisanProfile> findByStatus(ArtisanStatus status);
    Optional<ArtisanProfile> findBySlug(String slug);
    List<ArtisanProfile> findByPublishedTrueAndStatus(ArtisanStatus status);

    // Annuaire public : uniquement les vitrines publiées d'ateliers vérifiés, dans un ordre stable.
    List<ArtisanProfile> findByPublishedTrueAndStatusOrderByAtelierNameAsc(ArtisanStatus status);
}
