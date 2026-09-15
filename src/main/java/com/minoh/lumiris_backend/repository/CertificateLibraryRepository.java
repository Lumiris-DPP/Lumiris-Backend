package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.CertificateLibraryItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateLibraryRepository extends JpaRepository<CertificateLibraryItem, UUID> {

    // La liste rend le nom et l'URL signée de chaque fichier : sans le graphe, chaque certificat
    // déclenche sa propre lecture de `files`.
    @EntityGraph(attributePaths = "file")
    List<CertificateLibraryItem> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<CertificateLibraryItem> findByIdAndUser_Id(UUID id, UUID userId);
}
