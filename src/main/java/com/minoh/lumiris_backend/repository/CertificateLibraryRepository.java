package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.CertificateLibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateLibraryRepository extends JpaRepository<CertificateLibraryItem, UUID> {

    List<CertificateLibraryItem> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<CertificateLibraryItem> findByIdAndUser_Id(UUID id, UUID userId);
}
