package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanSource;
import com.minoh.lumiris_backend.entity.ArtisanStatus;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtisanProfileRepository extends JpaRepository<ArtisanProfile, UUID> {
    Optional<ArtisanProfile> findByUser(User user);
    List<ArtisanProfile> findByStatus(ArtisanStatus status);
    Optional<ArtisanProfile> findBySlug(String slug);
    List<ArtisanProfile> findByPublishedTrueAndStatus(ArtisanStatus status);
    Optional<ArtisanProfile> findBySourceAndExternalRef(ArtisanSource source, String externalRef);

    // Annuaire public : vitrines publiées d'ateliers vérifiés ET fiches importées non réclamées
    // (pattern "Doctolib" — voir ArtisanStatus.UNCLAIMED), dans un ordre stable.
    List<ArtisanProfile> findByPublishedTrueAndStatusInOrderByAtelierNameAsc(Collection<ArtisanStatus> statuses);

    // Signal d'intérêt anonyme — incrément atomique, pas de lecture-modification-écriture.
    @Modifying
    @Query("update ArtisanProfile p set p.interestCount = p.interestCount + 1 where p.id = :id")
    int incrementInterest(@Param("id") UUID id);
}
