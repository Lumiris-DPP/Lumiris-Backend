package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.ArtisanProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArtisanProfileRepository extends JpaRepository<ArtisanProfile, UUID> {
}
