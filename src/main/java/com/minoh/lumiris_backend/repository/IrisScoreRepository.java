package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.IrisScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IrisScoreRepository extends JpaRepository<IrisScore, UUID> {
    Optional<IrisScore> findByDppFormId(UUID dppFormId);
}
