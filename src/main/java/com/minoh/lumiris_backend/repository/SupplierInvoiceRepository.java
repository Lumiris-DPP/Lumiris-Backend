package com.minoh.lumiris_backend.repository;

import com.minoh.lumiris_backend.entity.SupplierInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, UUID> {

    List<SupplierInvoice> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<SupplierInvoice> findByIdAndUser_Id(UUID id, UUID userId);
}
