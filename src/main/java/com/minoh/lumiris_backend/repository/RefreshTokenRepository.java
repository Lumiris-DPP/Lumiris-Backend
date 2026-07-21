package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Révocation de toutes les sessions d'un utilisateur (déconnexion globale / suppression de compte).
    @Modifying
    @Transactional
    void deleteByUser_Id(UUID userId);
}
