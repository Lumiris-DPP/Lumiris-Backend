package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.GeocodeCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, Long> {
    Optional<GeocodeCache> findByQueryNormalized(String queryNormalized);
}