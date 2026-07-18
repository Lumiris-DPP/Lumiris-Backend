package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import com.minoh.lumiris_backend.entity.ArtisanProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArtisanProfilePhotoRepository extends JpaRepository<ArtisanProfilePhoto, UUID> {
    List<ArtisanProfilePhoto> findByArtisanProfileOrderByPosition(ArtisanProfile artisanProfile);
    int countByArtisanProfile(ArtisanProfile artisanProfile);
}
