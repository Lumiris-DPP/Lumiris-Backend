package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.SupplierInvoice;
import com.minoh.lumiris_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {
    List<SupplierInvoice> findByUploadedByOrderByCreatedAtDesc(User uploadedBy);
    Optional<SupplierInvoice> findByIdAndUploadedBy(UUID id, User uploadedBy);
}
